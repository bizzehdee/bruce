# The vtracer Python binding segfaults on Python 3.14; use the CLI

Established: 2026-09-27.

`vtracer` 0.6.15 from PyPI, run on Python 3.14.7, crashed with a segmentation fault
on almost every `convert_image_to_svg_py` call, with any parameter set, on both
palette and 32-bit PNG input. One call with default parameters succeeded; the
same call later failed without writing output.

The CLI built with `cargo install vtracer --version 0.6.4 --locked` traced the same
image first time. The logo (`docs/branding/bruce-logo.svg`) was traced with it using:
`--colormode color --hierarchical stacked --mode spline --filter_speckle 5
--color_precision 8 --gradient_step 6 --corner_threshold 60 --segment_length 4
--splice_threshold 45 --path_precision 2`, on the reference cropped to the head
(750 × 750 at +333+9), denoised with a 3 × 3 median and reduced to 13 colours
(`-kmeans 13`).
