# A utility script reading computed images and display them via OpenCV 
import cv2
import numpy as np
import matplotlib.pyplot as plt
import argparse

# simple arguments parser
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Simple stereo matching example.')
    parser.add_argument('--imgWidth', type=int, help='image width', required=True)
    parser.add_argument('--imgHeight', type=int, help='image height', required=True)
    args = parser.parse_args()

    # read raw disparity map bytes
    with open('output/intermediate/disparity', 'rb') as f:
        raw_disp = f.read()
    height, width = args.imgHeight, args.imgWidth
    # convert to numpy array
    disp = np.frombuffer(raw_disp, dtype=np.int8)
    # reshape to original shape
    disp = disp.reshape(int(height), int(width))
    print(f"max: {np.max(disp)}, min: {np.min(disp)}, mean: {np.mean(disp)}")
    # generate a distribution of the values
    plt.hist(disp.ravel())
    # save the histogram
    plt.savefig('output/img/c_histogram.png')
    # create a greyscale image from the numpy array
    disparity_normalized = cv2.normalize(disp, None, alpha=0, beta=255, norm_type=cv2.NORM_MINMAX, dtype=cv2.CV_8U)
    disparity_normalized = cv2.applyColorMap(disparity_normalized, cv2.COLORMAP_JET)
    # store the image
    cv2.imwrite('output/img/c_disparity.png', disparity_normalized)
