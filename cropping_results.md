# Preprocessing: Phone Number Cropping Results

The text detection script has successfully processed the raw photos to extract phone number crops based on the provided heuristics. 

Here are the results:
- **Successfully Cropped**: 60 images (saved in `./data/cropped/`)
- **Flagged for Manual Review**: 245 images (recorded in `review_needed.csv`)

## Sample Crops
Below is a grid of 15 sample crops that were detected automatically. Please review these to check the quality of the padding and bounding boxes:

![Sample Crops Grid](file:///Users/bhavya_agarwal/Desktop/Numcheck/sample_crops_grid.png)

_Note: For the 245 images that failed to yield a high-confidence crop automatically, their filenames are safely logged in `/Users/bhavya_agarwal/Desktop/Numcheck/review_needed.csv` so they won't silently pollute the training data._
