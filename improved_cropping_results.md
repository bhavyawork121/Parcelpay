# Improved Preprocessing: Phone Number Cropping Results (V2)

The improved 2-stage cropping script has processed all the remaining raw images. By over-detecting all possible text lines and applying a very strict heuristic (rejecting "TRACKING NO", "ADDRESS", house numbers, and enforcing a strict 10-digit primary structure), the precision has been significantly improved.

Here are the updated results:
- **Successfully Cropped (High Confidence)**: 57 images (saved in `./data/cropped/`)
- **Flagged for Manual Review**: 246 images (recorded in `review_needed.csv`)
- **Excluded (Screenshots/Non-parcels)**: 2 images (moved to `./data/excluded/` and logged in `excluded.csv`)

## New Sample Crops

Below is a random grid of 25 successful crops from the new pipeline. Notice how the strict filtering successfully avoided picking up addresses or tracking numbers this time:

![New Sample Crops Grid](file:///Users/bhavya_agarwal/Desktop/Numcheck/new_sample_crops_grid.png)
