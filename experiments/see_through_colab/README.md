# See-through on Colab

[`SeeThrough_T4_RUNALL.ipynb`](SeeThrough_T4_RUNALL.ipynb) runs
[See-through](https://github.com/shitagaki-lab/see-through) (single image →
layered PSD with inpainted hidden areas) on a Colab GPU at the official quality
settings: resolution 1280, 30 steps.

Why Colab: on the Mac mini (M4, 16 GB) the MPS backend asserts, CPU inference
peaks around 31 GB, and the earlier local run had to drop to resolution 512 and
1 step, which made the layers unusable.

- T4 (free tier): NF4 4-bit weights + group offload, ~8 GB VRAM.
- L4 / A100 (≥ 20 GB): full bf16 model, no offload.

Steps: Runtime → Change runtime type → T4 GPU → Run all → upload the image when
prompted → a zip with the PSD, layer PNGs and a contact sheet downloads at the end.

If Colab setup breaks, the authors' free
[Hugging Face Space](https://huggingface.co/spaces/24yearsold/see-through-demo)
runs the same model at 1280 (1–2 runs per day).

After downloading, fix the PSD, export full-canvas PNG layers and run
`tools/layer_qa/layer_motion_qa.py` before rigging in Cubism.
