# A utility script reading computed images and display them via OpenCV 
import cv2
import numpy as np
import matplotlib.pyplot as plt

# simple arguments parser
if __name__ == '__main__':

    # read raw disparity map bytes
    with open('output/intermediate/disparity', 'rb') as f:
        raw_disp = f.read()
    with open('output/intermediate/c_reference', 'r') as f:
        raw_ref = f.read()
    with open('output/intermediate/dimensions', 'r') as f:
        dimensions = f.read()
        height, width, min_disp, max_disp, block_size = dimensions.split(',')
    # convert to numpy array
    disp = np.frombuffer(raw_disp, dtype=np.int8)
    ref = np.frombuffer(raw_ref, dtype=np.int8)
    # reshape to original shape
    disp = disp.reshape(int(height), int(width))
    ref = ref.reshape(int(height), int(width))
    print(f"max: {np.max(disp)}, min: {np.min(disp)}, mean: {np.mean(disp)}")

    # create a greyscale image from the numpy array
    disparity_normalized = cv2.normalize(disp, None, alpha=0, beta=255, norm_type=cv2.NORM_MINMAX, dtype=cv2.CV_8U)
    disparity_normalized = cv2.applyColorMap(disparity_normalized, cv2.COLORMAP_JET)
    # store the image
    cv2.imwrite('output/img/otuput.png', disparity_normalized)

    ref_normalized = cv2.normalize(ref, None, alpha=0, beta=255, norm_type=cv2.NORM_MINMAX, dtype=cv2.CV_8U)
    ref_normalized = cv2.applyColorMap(ref_normalized, cv2.COLORMAP_JET)
    # store the image   
    cv2.imwrite('output/img/c_reference.png', ref_normalized)