// Builds Wanderlist's app icon from the logo artwork and writes every size Android and iOS need.
//
// Run from the repository root:   swift tools/app-icon/AppIcon.swift
//
// The source (logo-source.png, next to this file) is a picture of the logo: the artwork on a cream
// card with a ragged edge, inside a white border. Used as is, that border and edge would show
// inside every launcher mask. So the script finds the artwork, fades everything around it to one
// flat cream, and re-centres the artwork at the size each platform's safe area allows. To change
// the logo, replace logo-source.png (artwork on a plain light background) and run the script again.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!

// MARK: - Source

struct Bitmap {
    let width: Int
    let height: Int
    var pixels: [UInt8] // RGBA, top row first

    func channel(_ x: Int, _ y: Int, _ c: Int) -> Int { Int(pixels[(y * width + x) * 4 + c]) }
}

func load(_ path: String) -> Bitmap {
    guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
    else { fatalError("Couldn't read \(path)") }
    var bitmap = Bitmap(width: image.width, height: image.height, pixels: [UInt8](repeating: 0, count: image.width * image.height * 4))
    let ctx = CGContext(
        data: &bitmap.pixels, width: image.width, height: image.height, bitsPerComponent: 8,
        bytesPerRow: image.width * 4, space: sRGB, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
    return bitmap
}

var logo = load("tools/app-icon/logo-source.png")
let w = logo.width, h = logo.height

/// The card's cream, averaged over a patch near its top-left corner (inside the white border).
let cream: [Int] = {
    var sum = [0, 0, 0], count = 0
    for y in h / 10..<h * 18 / 100 { for x in w / 10..<w * 18 / 100 {
        for c in 0..<3 { sum[c] += logo.channel(x, y, c) }
        count += 1
    } }
    return sum.map { $0 / count }
}()
let background = CGColor(srgbRed: CGFloat(cream[0]) / 255, green: CGFloat(cream[1]) / 255, blue: CGFloat(cream[2]) / 255, alpha: 1)

/// How far a pixel is from the cream: the largest difference of its three channels.
func distanceFromCream(_ x: Int, _ y: Int) -> Int {
    (0..<3).map { abs(logo.channel(x, y, $0) - cream[$0]) }.max()!
}

/// The bounding box of the pixels further than [threshold] from the cream. Only the inner 80% is
/// searched, so the border and the card's ragged edge never count.
func bounds(threshold: Int) -> CGRect {
    var x0 = w, y0 = h, x1 = 0, y1 = 0
    for y in h / 10..<h * 9 / 10 { for x in w / 10..<w * 9 / 10 where distanceFromCream(x, y) > threshold {
        x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
    } }
    return CGRect(x: x0, y: y0, width: x1 - x0 + 1, height: y1 - y0 + 1)
}

/// The artwork's box (what gets centred and sized).
let artwork = bounds(threshold: 18)
let centre = CGPoint(x: artwork.midX, y: artwork.midY)

/// The artwork pixel furthest from the centre: what has to fit inside a circular mask.
let artworkRadius: CGFloat = {
    var furthest: CGFloat = 0
    for y in Int(artwork.minY)..<Int(artwork.maxY) { for x in Int(artwork.minX)..<Int(artwork.maxX) where distanceFromCream(x, y) > 18 {
        furthest = max(furthest, hypot(CGFloat(x) + 0.5 - centre.x, CGFloat(y) + 0.5 - centre.y))
    } }
    return furthest
}()

/// Each pixel's distance (in px) to the nearest pixel of the artwork or its soft shadow (further
/// than 10 levels from the cream), by a two-pass chamfer distance transform. Following the outline
/// rather than a box matters: the source has faint smudges near its corners, inside the box.
let distanceToArtwork: [CGFloat] = {
    let far = CGFloat(w + h)
    var d = [CGFloat](repeating: far, count: w * h)
    for y in h / 10..<h * 9 / 10 { for x in w / 10..<w * 9 / 10 where distanceFromCream(x, y) > 10 { d[y * w + x] = 0 } }
    let diagonal: CGFloat = 1.4142
    for y in 0..<h { for x in 0..<w {
        var v = d[y * w + x]
        if x > 0 { v = min(v, d[y * w + x - 1] + 1) }
        if y > 0 {
            v = min(v, d[(y - 1) * w + x] + 1)
            if x > 0 { v = min(v, d[(y - 1) * w + x - 1] + diagonal) }
            if x < w - 1 { v = min(v, d[(y - 1) * w + x + 1] + diagonal) }
        }
        d[y * w + x] = v
    } }
    for y in stride(from: h - 1, through: 0, by: -1) { for x in stride(from: w - 1, through: 0, by: -1) {
        var v = d[y * w + x]
        if x < w - 1 { v = min(v, d[y * w + x + 1] + 1) }
        if y < h - 1 {
            v = min(v, d[(y + 1) * w + x] + 1)
            if x < w - 1 { v = min(v, d[(y + 1) * w + x + 1] + diagonal) }
            if x > 0 { v = min(v, d[(y + 1) * w + x - 1] + diagonal) }
        }
        d[y * w + x] = v
    } }
    return d
}()

// Fade everything around the artwork to the flat cream: fully kept up to 12 px from it, fully
// cream 30 px further out. The cream there only varies by a level or two, so the fade can't be
// seen, and the result can be scaled and placed on a cream backdrop of any size without a seam.
let keep: CGFloat = 12
let fade: CGFloat = 30
for y in 0..<h { for x in 0..<w {
    let t = min(max(distanceToArtwork[y * w + x] - keep, 0) / fade, 1)
    let amount = t * t * (3 - 2 * t) // smoothstep
    for c in 0..<3 {
        let i = (y * w + x) * 4 + c
        logo.pixels[i] = UInt8((CGFloat(logo.pixels[i]) * (1 - amount) + CGFloat(cream[c]) * amount).rounded())
    }
    logo.pixels[(y * w + x) * 4 + 3] = 255
} }

let cleanLogo: CGImage = logo.pixels.withUnsafeMutableBytes { bytes in
    CGContext(
        data: bytes.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
        space: sRGB, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!.makeImage()!
}

print("cream #\(cream.map { String(format: "%02X", $0) }.joined()), artwork \(artwork), radius \(Int(artworkRadius)) px")

// MARK: - Output

enum Backdrop {
    case fill                        // the whole canvas (iOS and the adaptive foreground)
    case roundedSquare(inset: Bool)  // legacy Android launcher icon (inset) and the in-app logo
    case circle                      // legacy Android round icon
}

enum Fit {
    case height(CGFloat)             // the artwork's height as a fraction of the size
    case radius(CGFloat)             // the artwork's furthest pixel from its centre, as a fraction
}

/// Renders a [size] px icon: the [backdrop] in cream, with the artwork centred and scaled to [fit].
/// A full-canvas backdrop is written without an alpha channel: the App Store rejects icons that
/// have one, even when nothing is transparent.
func render(size: Int, backdrop: Backdrop, fit: Fit, to path: String) {
    let s = CGFloat(size)
    let opaque = if case .fill = backdrop { true } else { false }
    let ctx = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: sRGB,
        bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue
    )!
    ctx.setShouldAntialias(true)
    ctx.interpolationQuality = .high

    // CoreGraphics' origin is bottom-left; every shape here is symmetric, so only the image
    // placement below needs to think about it.
    let inset = s / 24 // legacy icons leave a small margin, as Android's own templates do
    let shape: CGPath
    switch backdrop {
    case .fill:
        shape = CGPath(rect: CGRect(x: 0, y: 0, width: s, height: s), transform: nil)
    case .roundedSquare(let hasInset):
        let rect = CGRect(x: 0, y: 0, width: s, height: s).insetBy(dx: hasInset ? inset : 0, dy: hasInset ? inset : 0)
        let corner = hasInset ? s * 0.18 : s * 0.2237 // the in-app logo uses iOS's icon corner
        shape = CGPath(roundedRect: rect, cornerWidth: corner, cornerHeight: corner, transform: nil)
    case .circle:
        shape = CGPath(ellipseIn: CGRect(x: inset, y: inset, width: s - 2 * inset, height: s - 2 * inset), transform: nil)
    }
    ctx.addPath(shape)
    ctx.setFillColor(background)
    ctx.fillPath()

    let scale: CGFloat = switch fit {
    case .height(let fraction): fraction * s / artwork.height
    case .radius(let fraction): fraction * s / artworkRadius
    }
    // Place the source so the artwork's centre lands on the canvas centre (source y runs down,
    // the context's y runs up, hence the flip of the vertical offset).
    let drawn = CGRect(
        x: s / 2 - centre.x * scale,
        y: s / 2 - (CGFloat(h) - centre.y) * scale,
        width: CGFloat(w) * scale,
        height: CGFloat(h) * scale
    )
    ctx.saveGState()
    ctx.addPath(shape)
    ctx.clip()
    ctx.draw(cleanLogo, in: drawn)
    ctx.restoreGState()

    let url = URL(fileURLWithPath: path)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, ctx.makeImage()!, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("Couldn't write \(path)") }
    print("wrote \(path) (\(size)px)")
}

// Android adaptive foreground: a 108dp canvas, 432 px at xxxhdpi, opaque cream like the background
// layer. Launcher masks only ever cut outside the 66dp circle (radius 0.306 of the canvas), so the
// artwork's furthest pixel stays at 0.29.
let res = "androidApp/src/main/res"
render(size: 432, backdrop: .fill, fit: .radius(0.29), to: "\(res)/drawable-nodpi/ic_launcher_foreground.png")

// Android legacy icons (API 24-25 and launchers without adaptive icons).
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(size: size, backdrop: .roundedSquare(inset: true), fit: .height(0.66), to: "\(res)/mipmap-\(density)/ic_launcher.png")
    render(size: size, backdrop: .circle, fit: .radius(0.38), to: "\(res)/mipmap-\(density)/ic_launcher_round.png")
}

// iOS: one 1024 px image; the system masks the corners. Opaque, since App Store icons can't have alpha.
let appIcon = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
render(size: 1024, backdrop: .fill, fit: .height(0.76), to: "\(appIcon)/app-icon-1024.png")

// The logo inside the app (sign-in screen): the icon as a rounded tile on a transparent
// background, so it suits light and dark themes. 384 px covers the largest size it's drawn at
// (112dp at 3x).
render(size: 384, backdrop: .roundedSquare(inset: false), fit: .height(0.76), to: "shared/src/commonMain/composeResources/drawable/app_logo.png")
