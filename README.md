# Sail Painter

A [RuneLite](https://runelite.net) plugin that lets you draw a picture and see it on the sail of
your boat.

Only you can see it. The game has no way to change what a sail looks like, so the plugin paints
your picture over the sail on your own screen, every frame, following the cloth as it billows and
the boat as it turns. It changes nothing in the game and sends nothing anywhere.

## Drawing

Open the **Sail Painter** button in the sidebar, then **Open drawing studio**.

- **Brush**, **Eraser**, **Fill**, **Pick** (eyedropper), **Line**, **Box** and **Oval**, with a
  size slider. Right-click rubs out with any tool. Hold Shift to draw straight lines at 45 degrees,
  squares and circles.
- **Mirror drawing** draws everything again reflected left to right, for symmetrical designs.
- A palette, plus **More colours** for any colour at all.
- **Canvas** sizes from 32 × 32 for chunky pixel art up to 256 × 256.
- **Examples** to start from, including a **Test pattern** that shows which way up and which way
  round the picture lands.
- **Import** any picture from your computer, and **Export** yours as a PNG.
- Undo and redo (Ctrl+Z, Ctrl+Y), and single-key tool shortcuts shown in each tool's tooltip.

Your sail updates as you draw. Whatever you leave empty shows the sail's own colour through.

The design is kept in `.runelite/sail-painter/sail.png`.

## How it looks

The picture is laid across the sail's cloth and picks up the game's own light and shade, so it
follows the folds. Your character, your crew, the mast and everything else on the boat still stand
in front of it. Ropes and spars on the sail are left alone.

## Settings

- **Paint my sail** - on or off. Your own boat is painted, and so is whichever boat you are aboard.
- **Readable from both sides** - shows the picture the right way round from behind the sail too.
  Off, it shows mirrored from behind, like paint soaking through the cloth.
- **Picture** - *Fit inside the sail* puts the whole picture in the biggest box that fits on the
  cloth, so the raft's triangular sail cuts none of it off. *Stretch over the sail* covers all the
  cloth instead, losing the corners on a triangular sail. Square sails look the same either way.
- **Opacity**, **Shading** and **Smooth picture** (for photos rather than pixel art).
- **Hide behind crew and rigging** - lets things in front of the sail cover the picture.
- Under **Troubleshooting**: **Paint** whole sail model instead of just the cloth, and **Show debug
  info**, which labels every object on your boat with its ID.

## Building

    ./gradlew build
