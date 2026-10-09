# Sail Painter

A [RuneLite](https://runelite.net) plugin that lets you draw a picture and see it on the sail of
your boat, and share it with friends in your RuneLite party.

The game has no way to change what a sail looks like, so the plugin paints your picture over the
sail on your own screen, every frame, following the cloth as it billows and the boat as it turns.
It changes nothing in the game. Unless you share it with your party, nobody else sees it and
nothing is sent anywhere.

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

The design is kept in `.runelite/plugin-data/sail-painter/sail.png`.

## How it looks

The picture is laid across the sail's cloth and picks up the game's own light and shade, so it
follows the folds. Your character, your crew, the mast and everything else on the boat still stand
in front of it. Ropes and spars on the sail are left alone.

## Sharing with your party

Turn on **Share with party** and everyone in your RuneLite party (from the Party panel in the sidebar)
who also has Sail Painter sees your design on your boat, and you see theirs on theirs. Designs go
through RuneLite's own party service, the same one the Party plugin uses, so there is no other
server involved.

- A friend's design goes on whichever boat they are aboard, so it shows while they are on their
  boat, not while it is moored without them.
- Large or very detailed designs are shrunk before they are sent.
- Turning sharing off, or turning the plugin off, puts your sail back to plain on their screens.
- **Show party sails** turns other people's designs off on your screen.

## Community sails

Turn on **Community sails** in the settings to see the sails other Sail Painter players have had
approved, on whichever boat they are aboard, and to put yours up for everyone to see. It is off
until you turn it on, because it talks to a server outside RuneLite: https://sails.shep.rip.

- **Submit my sail for review** in the sidebar sends your current design, with your character name,
  to the Sail Painter moderator. Nobody else sees it until it has been approved. Once it has, it
  appears on your boat for everyone with Community sails on, and in the gallery at
  https://sails.shep.rip. The sidebar shows whether it is waiting, approved or turned down.
- **Stop showing my sail** takes it down again.
- **Sails near you** lists the community sails on boats around you. **Hide** stops one being drawn
  for you, and only you; **Report** sends it to the moderator with a reason and hides it for you.
- **View all sails online** opens the gallery.

The plugin downloads the whole list of approved sails and keeps the pictures in
`.runelite/plugin-data/sail-painter/community`, then matches names to boats on your own computer.
The server is never told who is around you. Submitting sends your account hash, so that only you
can change your sail; the server stores only a salted hash of it. A report sends your account hash
too, so each person's report counts once; the moderator never sees who reported.

Party sharing still works as before and needs no server. A party member's shared design wins over
their community sail.

## Settings

- **Paint my sail** - on or off. Your own boat is painted, and so is whichever boat you are aboard.
- **Readable from both sides** - shows the picture the right way round from behind the sail too.
  Off, it shows mirrored from behind, like paint soaking through the cloth.
- **Picture** - *Fit inside the sail* puts the whole picture in the biggest box that fits on the
  cloth, so the raft's triangular sail cuts none of it off. *Stretch over the sail* covers all the
  cloth instead, losing the corners on a triangular sail. Square sails look the same either way.
- **Opacity**, **Shading** and **Smooth picture** (for photos rather than pixel art).
- **Hide behind crew and rigging** - lets things in front of the sail cover the picture.
- Under **Community**: **Community sails** (off until you turn it on).
- Under **Party**: **Share with party** (off until you turn it on) and **Show party sails**.
- Under **Troubleshooting**: **Paint** whole sail model instead of just the cloth, and **Show debug
  info**, which labels every object on your boat with its ID.

## Building

    ./gradlew build
