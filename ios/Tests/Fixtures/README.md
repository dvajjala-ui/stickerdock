# Native media integrity fixtures

`motion.gif` is an original programmatically drawn moving shape, four frames at
250 ms. `motion.mp4` is FFmpeg's synthetic testsrc2 at 320x180/20 fps/1.5 seconds;
`motion-rotated.mp4` is the same fixture with a 90-degree display matrix. They
contain no user's photos, third-party meme art, sound or private metadata.

These fixtures exercise actual Apple GIF/video codecs, rotation and moving WebP
output. They are software integrity tests, never evidence that product sticker
content quality is acceptable. Real human/animal trials are separately credited
and retained in ignored work media for visual/Android acceptance.

The rotated fixture is remuxed with FFmpeg's input `-display_rotation 90`
option. The older `-metadata:s:v:0 rotate=90` command silently produced no
matrix with this FFmpeg build. FFmpeg now reports a 90-degree display matrix
and 180x320 display output; the native test independently loads and checks the
track's preferred transform before checking the sticker's portrait pixels.
