// Draws Wanderlist's app icon (a gold pocket compass) and writes every size Android and iOS need.
//
// Run from the repository root:   swift tools/app-icon/AppIcon.swift
//
// The compass is vector-drawn with CoreGraphics, so each file is rendered at its exact pixel size
// (no upscaling). It is laid out around the dial's centre, not the centre of its bounding box: the
// ring at the top right would otherwise pull the dial off-centre. Sizes follow each platform's
// safe area: on Android everything stays inside the 66dp circle no launcher mask ever cuts.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// MARK: - Colours

func rgb(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

/// The Android adaptive icon background (values/ic_launcher_background.xml) and the iOS background.
let background = rgb(0xFFF9F0)
let goldLight = rgb(0xFFD84A)
let goldDark = rgb(0xF2A900)
let goldEdge = rgb(0xDE9700)
let goldBevel = rgb(0xEBA500)
let face = rgb(0xFFFCF5)
let tick = rgb(0xB8AB98)
let roseLight = rgb(0xEADBC4)
let roseDark = rgb(0xCDB693)
let roseMinorLight = rgb(0xC9B18F)
let roseMinorDark = rgb(0xAE9573)
let redLight = rgb(0xF2503F)
let redDark = rgb(0xD63A2E)
let tealLight = rgb(0x22899B)
let tealDark = rgb(0x176B79)
let cap = rgb(0xBBD7E0)
let capEdge = rgb(0x8DB2C0)

// MARK: - Drawing

/// A point at [distance] from [center] in direction [degrees] (0 = up, clockwise; y grows downwards).
func polar(_ center: CGPoint, _ degrees: CGFloat, _ distance: CGFloat) -> CGPoint {
    let radians = degrees * .pi / 180
    return CGPoint(x: center.x + sin(radians) * distance, y: center.y - cos(radians) * distance)
}

func fillCircle(_ ctx: CGContext, _ center: CGPoint, _ radius: CGFloat, _ color: CGColor) {
    ctx.setFillColor(color)
    ctx.fillEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

func fillTriangle(_ ctx: CGContext, _ a: CGPoint, _ b: CGPoint, _ c: CGPoint, _ color: CGColor) {
    ctx.setFillColor(color)
    ctx.beginPath()
    ctx.move(to: a)
    ctx.addLine(to: b)
    ctx.addLine(to: c)
    ctx.closePath()
    ctx.fillPath()
}

/// A gold disc with a top-left to bottom-right gradient.
func goldDisc(_ ctx: CGContext, _ center: CGPoint, _ radius: CGFloat) {
    ctx.saveGState()
    ctx.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    ctx.clip()
    let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [goldLight, goldDark] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(
        gradient,
        start: CGPoint(x: center.x - radius, y: center.y - radius),
        end: CGPoint(x: center.x + radius, y: center.y + radius),
        options: []
    )
    ctx.restoreGState()
}

/// One point of the compass rose, split down its middle into a light and a dark half.
func rosePoint(_ ctx: CGContext, _ c: CGPoint, _ degrees: CGFloat, length: CGFloat, width: CGFloat, light: CGColor, dark: CGColor) {
    let tip = polar(c, degrees, length)
    fillTriangle(ctx, c, tip, polar(c, degrees - 45, width), light)
    fillTriangle(ctx, c, tip, polar(c, degrees + 45, width), dark)
}

/// The compass, its dial centred on [c], with case radius [r]. Everything, the ring included,
/// stays within 1.35 r of the centre. The hole in the ring shows [behind]: the backdrop's
/// colour, or nil to cut it out (transparent).
func drawCompass(_ ctx: CGContext, _ c: CGPoint, _ r: CGFloat, behind: CGColor?) {
    // The ring at the top right, behind the case: a neck, then the ring itself.
    let ringAngle: CGFloat = 45
    let ringCenter = polar(c, ringAngle, 1.17 * r)
    fillCircle(ctx, polar(c, ringAngle, 1.0 * r), 0.13 * r, goldEdge)
    fillCircle(ctx, ringCenter, 0.18 * r, goldEdge)
    fillCircle(ctx, ringCenter, 0.155 * r, goldDark)
    if let behind {
        fillCircle(ctx, ringCenter, 0.085 * r, behind)
    } else {
        ctx.saveGState()
        ctx.setBlendMode(.clear)
        fillCircle(ctx, ringCenter, 0.085 * r, rgb(0x000000))
        ctx.restoreGState()
    }

    // The case: an edge, the gold body, a darker bevel, then the face.
    fillCircle(ctx, c, r, goldEdge)
    goldDisc(ctx, c, 0.965 * r)
    fillCircle(ctx, c, 0.83 * r, goldBevel)
    fillCircle(ctx, c, 0.78 * r, face)

    // Ticks: dots at north, east, south and west, short dashes between them.
    for i in 0..<8 {
        let degrees = CGFloat(i) * 45
        if i % 2 == 0 {
            fillCircle(ctx, polar(c, degrees, 0.665 * r), 0.042 * r, tick)
        } else {
            ctx.setStrokeColor(tick)
            ctx.setLineWidth(0.04 * r)
            ctx.setLineCap(.round)
            ctx.beginPath()
            ctx.move(to: polar(c, degrees, 0.62 * r))
            ctx.addLine(to: polar(c, degrees, 0.71 * r))
            ctx.strokePath()
        }
    }

    // The rose: four short diagonal points behind four long ones.
    for i in 0..<4 {
        rosePoint(ctx, c, 45 + CGFloat(i) * 90, length: 0.40 * r, width: 0.10 * r, light: roseMinorLight, dark: roseMinorDark)
    }
    for i in 0..<4 {
        rosePoint(ctx, c, CGFloat(i) * 90, length: 0.58 * r, width: 0.12 * r, light: roseLight, dark: roseDark)
    }

    // The needle: red towards the top right, teal towards the bottom left, each in two shades.
    let north: CGFloat = 45
    let red = polar(c, north, 0.56 * r)
    let teal = polar(c, north + 180, 0.56 * r)
    let left = polar(c, north - 90, 0.095 * r)
    let right = polar(c, north + 90, 0.095 * r)
    fillTriangle(ctx, red, left, c, redLight)
    fillTriangle(ctx, red, right, c, redDark)
    fillTriangle(ctx, teal, left, c, tealLight)
    fillTriangle(ctx, teal, right, c, tealDark)

    // The cap over the pivot, with a small highlight.
    fillCircle(ctx, c, 0.115 * r, capEdge)
    fillCircle(ctx, c, 0.09 * r, cap)
    fillCircle(ctx, CGPoint(x: c.x - 0.03 * r, y: c.y - 0.03 * r), 0.035 * r, rgb(0xFFFFFF, 0.75))
}

// MARK: - Output

enum Backdrop {
    case none                        // transparent (adaptive foreground, iOS dark)
    case fill                        // the whole canvas (iOS: the system rounds the corners)
    case roundedSquare               // legacy Android launcher icon
    case circle                      // legacy Android round icon
}

/// Renders a [size] px icon: [backdrop] in the background colour, and the compass with its dial
/// centred and its case radius [caseRatio] of the size. A full-canvas backdrop is written without
/// an alpha channel: the App Store rejects icons that have one, even when nothing is transparent.
func render(size: Int, backdrop: Backdrop, caseRatio: CGFloat, to path: String) {
    let s = CGFloat(size)
    let opaque = backdrop == .fill
    let ctx = CGContext(
        data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!,
        bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue
    )!
    // Top-left origin, y downwards, like the design coordinates above.
    ctx.translateBy(x: 0, y: s)
    ctx.scaleBy(x: 1, y: -1)
    ctx.setShouldAntialias(true)
    ctx.interpolationQuality = .high

    let inset = s / 24 // legacy icons leave a small margin, as Android's own templates do
    switch backdrop {
    case .none:
        break
    case .fill:
        ctx.setFillColor(background)
        ctx.fill(CGRect(x: 0, y: 0, width: s, height: s))
    case .roundedSquare:
        let rect = CGRect(x: inset, y: inset, width: s - 2 * inset, height: s - 2 * inset)
        ctx.addPath(CGPath(roundedRect: rect, cornerWidth: s * 0.18, cornerHeight: s * 0.18, transform: nil))
        ctx.setFillColor(background)
        ctx.fillPath()
    case .circle:
        fillCircle(ctx, CGPoint(x: s / 2, y: s / 2), s / 2 - inset, background)
    }

    // Wherever there's a backdrop behind the ring (all but the transparent ones), its hole shows it.
    drawCompass(ctx, CGPoint(x: s / 2, y: s / 2), caseRatio * s, behind: backdrop == .none ? nil : background)

    let url = URL(fileURLWithPath: path)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, ctx.makeImage()!, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("Couldn't write \(path)") }
    print("wrote \(path) (\(size)px)")
}

// Android adaptive foreground: a 108dp canvas, 432 px at xxxhdpi. The compass reaches 1.35 × its
// case radius, so a case radius of 0.226 keeps it all inside the 66dp (0.611) safe circle.
let res = "androidApp/src/main/res"
render(size: 432, backdrop: .none, caseRatio: 0.226, to: "\(res)/drawable-nodpi/ic_launcher_foreground.png")

// Android legacy icons (API 24-25 and launchers without adaptive icons).
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    render(size: size, backdrop: .roundedSquare, caseRatio: 0.27, to: "\(res)/mipmap-\(density)/ic_launcher.png")
    render(size: size, backdrop: .circle, caseRatio: 0.27, to: "\(res)/mipmap-\(density)/ic_launcher_round.png")
}

// iOS: one 1024 px image; the system masks the corners. Opaque (App Store icons can't have alpha),
// plus a dark-mode version on a transparent background, which iOS puts on its own dark backdrop.
let appIcon = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
render(size: 1024, backdrop: .fill, caseRatio: 0.30, to: "\(appIcon)/app-icon-1024.png")
render(size: 1024, backdrop: .none, caseRatio: 0.30, to: "\(appIcon)/app-icon-1024-dark.png")

// The logo inside the app (sign-in screen): the compass on a transparent background, filling its
// frame (1.35 × 0.37 = 0.5 of the size from the dial's centre), so it suits light and dark themes.
// 384 px covers the largest size it's drawn at (112dp at 3x).
render(size: 384, backdrop: .none, caseRatio: 0.37, to: "shared/src/commonMain/composeResources/drawable/app_logo.png")
