// One-time migration of place photos saved before private photo access (commit 06a9832).
//
// Before that version, each photo document stored a public download link (`url`), photo markers
// stored one too (`marker.photoUrl`), photos had no thumbnail, and every file had a download token
// that lets anyone holding its link download it. This script, for every place:
//   1. makes the missing thumbnails (`{photoId}_thumb.jpg`, 480 px on the short side, JPEG 80%,
//      the same as the app's PhotoCompressor);
//   2. deletes the stored links from photo documents and markers;
//   3. adds photos missing from the place's photo index (`photoUploaders`; places saved before
//      the index existed have none);
//   4. removes the download token from every file under places/, so old links stop working.
// It also reports files no photo document points at, and photos whose file is missing; it
// changes neither. After migrating it checks its work: each token is read back, and a few of the
// old links are tried, which must now be refused.
//
// Run it only after every device has the private-access version: older versions load photos
// from the links this removes.
//
// Usage, from the repository root:
//   cd tools/migrate-photos && npm ci
//   node migrate.mjs --key /path/to/service-account.json            # dry run: changes nothing
//   node migrate.mjs --key /path/to/service-account.json --apply    # does it
//
// The key comes from the Firebase console: Project settings > Service accounts > Generate new
// private key. It can do anything in the project: keep it outside the repository and delete it
// (in the console too) when done. The bucket is read from androidApp/google-services.json, or
// given with --bucket. Safe to run again: anything already done is skipped.

import { readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { cert, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import sharp from "sharp";

const THUMBNAIL_SHORT_EDGE_PX = 480;
const JPEG_QUALITY = 80;
const TOKEN_KEY = "firebaseStorageDownloadTokens";

const { values: args } = parseArgs({
  options: {
    key: { type: "string" },
    bucket: { type: "string" },
    apply: { type: "boolean", default: false },
  },
});

// With the emulators running (FIRESTORE_EMULATOR_HOST and FIREBASE_STORAGE_EMULATOR_HOST set),
// no key is needed; GCLOUD_PROJECT names the project.
const usingEmulators = Boolean(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_STORAGE_EMULATOR_HOST);
if (!args.key && !usingEmulators) {
  console.error("Usage: node migrate.mjs --key <service-account.json> [--bucket <name>] [--apply]");
  process.exit(1);
}
const bucketName = args.bucket ?? defaultBucket();
initializeApp({
  ...(args.key ? { credential: cert(JSON.parse(readFileSync(args.key, "utf8"))) } : {}),
  storageBucket: bucketName,
});
const db = getFirestore();
const bucket = getStorage().bucket();
const apply = args.apply;

const totals = {
  places: 0, photos: 0, thumbnailsMade: 0, linksRemoved: 0, indexEntriesAdded: 0,
  markerLinksRemoved: 0, tokensRemoved: 0, missingFiles: [], orphanFiles: [], failures: [],
};
/** Old links removed in this run, to check afterwards that they no longer work. */
const oldLinks = [];

console.log(`${apply ? "Migrating" : "Dry run (nothing is changed; add --apply to migrate)"}: bucket ${bucketName}\n`);

// Every file under places/, by path, so each one is fetched from Storage once.
const [files] = await bucket.getFiles({ prefix: "places/" });
const filesByPath = new Map(files.map((file) => [file.name, file]));
const referencedPaths = new Set();

const places = await db.collection("places").get();
for (const place of places.docs) {
  totals.places++;
  const data = place.data();
  const index = data.photoUploaders ?? {};
  const photos = await place.ref.collection("photos").get();
  const placeLog = [];

  // The place document: photos missing from the index, and the marker's stored link.
  const placeUpdate = {};
  for (const photo of photos.docs) {
    if (!(photo.id in index)) {
      placeUpdate[`photoUploaders.${photo.id}`] = photo.data().uploaderId;
      totals.indexEntriesAdded++;
    }
  }
  if (typeof data.marker?.photoUrl === "string") {
    placeUpdate["marker.photoUrl"] = FieldValue.delete();
    totals.markerLinksRemoved++;
  }
  if (Object.keys(placeUpdate).length > 0) {
    const added = Object.keys(placeUpdate).filter((key) => key.startsWith("photoUploaders.")).length;
    if (added) placeLog.push(`add ${added} photo(s) to the index`);
    if ("marker.photoUrl" in placeUpdate) placeLog.push("remove the marker's link");
    // updatedAt stays as it is: this isn't an edit anyone made.
    if (apply) await place.ref.update(placeUpdate);
  }

  for (const photo of photos.docs) {
    totals.photos++;
    const { uploaderId, url } = photo.data();
    const path = `places/${place.id}/${uploaderId}/${photo.id}.jpg`;
    const thumbnailPath = `places/${place.id}/${uploaderId}/${photo.id}_thumb.jpg`;
    referencedPaths.add(path);
    referencedPaths.add(thumbnailPath);

    try {
      const file = filesByPath.get(path);
      if (!file) {
        totals.missingFiles.push(path);
      } else if (!filesByPath.has(thumbnailPath)) {
        placeLog.push(`make thumbnail for ${photo.id}`);
        totals.thumbnailsMade++;
        if (apply) await makeThumbnail(file, thumbnailPath);
      }
      if (typeof url === "string") {
        oldLinks.push(url);
        placeLog.push(`remove link from photo ${photo.id}`);
        totals.linksRemoved++;
        if (apply) await photo.ref.update({ url: FieldValue.delete() });
      }
    } catch (error) {
      totals.failures.push(`${path}: ${error.message}`);
    }
  }

  const name = data.customName || data.actualName || "(unnamed)";
  console.log(`${name} [${place.id}]: ${placeLog.length ? placeLog.join(", ") : "nothing to do"}`);
}

// Download tokens: every file under places/, photos and thumbnails alike (the app's uploads get
// one too). Thumbnails made above were uploaded by this script and never had one.
for (const file of files) {
  if (!referencedPaths.has(file.name)) totals.orphanFiles.push(file.name);
  try {
    if (file.metadata.metadata?.[TOKEN_KEY]) {
      totals.tokensRemoved++;
      if (apply) {
        // null deletes a custom metadata key.
        await file.setMetadata({ metadata: { [TOKEN_KEY]: null } });
        const [after] = await file.getMetadata();
        if (after.metadata?.[TOKEN_KEY]) throw new Error("the download token is still there after removing it");
      }
    }
  } catch (error) {
    totals.failures.push(`${file.name}: ${error.message}`);
  }
}

// The old links (real ones point at Firebase Storage, not the emulator) must be refused now.
if (apply && !usingEmulators) {
  for (const link of oldLinks.slice(0, 3)) {
    const response = await fetch(link, { method: "HEAD" });
    if (response.ok) totals.failures.push(`an old link still works: ${link}`);
    else console.log(`Old link refused (HTTP ${response.status}), as it should be.`);
  }
}

const count = (done, todo, value) => `  ${(apply ? done : todo).padEnd(28)}${value}`;
console.log(`
${totals.places} places, ${totals.photos} photos
${count("thumbnails made:", "thumbnails to make:", totals.thumbnailsMade)}
${count("photo links removed:", "photo links to remove:", totals.linksRemoved)}
${count("marker links removed:", "marker links to remove:", totals.markerLinksRemoved)}
${count("index entries added:", "index entries to add:", totals.indexEntriesAdded)}
${count("download tokens removed:", "download tokens to remove:", totals.tokensRemoved)}`);
report("Photos whose file is missing (not changed)", totals.missingFiles);
report("Files no photo points at (not changed)", totals.orphanFiles);
report("Failed (run again to retry)", totals.failures);
process.exit(totals.failures.length ? 1 : 0);

/** Makes [file]'s thumbnail the way the app does, and uploads it to [thumbnailPath]. */
async function makeThumbnail(file, thumbnailPath) {
  const [original] = await file.download();
  const { width, height, orientation } = await sharp(original).metadata();
  // EXIF orientations 5 to 8 turn the photo sideways. The app writes upright pixels with no
  // orientation, but this keeps any other photo right too.
  const sideways = orientation >= 5;
  const portrait = (sideways ? height : width) <= (sideways ? width : height);
  const thumbnail = await sharp(original)
    .rotate()
    .resize(portrait
      ? { width: THUMBNAIL_SHORT_EDGE_PX, withoutEnlargement: true }
      : { height: THUMBNAIL_SHORT_EDGE_PX, withoutEnlargement: true })
    .jpeg({ quality: JPEG_QUALITY })
    .toBuffer();
  await bucket.file(thumbnailPath).save(thumbnail, { contentType: "image/jpeg", resumable: false });
}

/** The project's bucket from the Android config file (not in Git). */
function defaultBucket() {
  try {
    const config = JSON.parse(readFileSync(new URL("../../androidApp/google-services.json", import.meta.url), "utf8"));
    return config.project_info.storage_bucket;
  } catch {
    console.error("Couldn't read the bucket from androidApp/google-services.json; pass --bucket <name>.");
    process.exit(1);
  }
}

function report(title, items) {
  if (items.length === 0) return;
  console.log(`\n${title}: ${items.length}`);
  items.forEach((item) => console.log(`  ${item}`));
}
