# A utility script reading images via OpenCV and storing them in intermediate forms for actual compute

import cv2
import numpy as np
import argparse

# simple arguments parser
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Simple stereo matching example.')
    parser.add_argument('--left', type=str, help='left image file', required=True)
    parser.add_argument('--right', type=str, help='right image file', required=True)
    parser.add_argument('--imgWidth', type=int, help='image width', required=True)
    parser.add_argument('--imgHeight', type=int, help='image height', required=True)
    parser.add_argument('--min_disp', type=int, help='minimum disparity', required=True)
    parser.add_argument('--max_disp', type=int, help='maximum disparity', required=True)
    parser.add_argument('--block_size', type=int, help='block size', required=True)
    args = parser.parse_args()

    # read images as grayscale
    imgL = cv2.imread(args.left)
    imgR = cv2.imread(args.right)

    # convert to grayscale
    gray_left = cv2.cvtColor(imgL, cv2.COLOR_BGR2GRAY, imgL)
    gray_right = cv2.cvtColor(imgR, cv2.COLOR_BGR2GRAY, imgR)

    # resize to the specified dimensions
    gray_left_resized = cv2.resize(gray_left, (args.imgWidth, args.imgHeight))
    gray_right_resized = cv2.resize(gray_right, (args.imgWidth, args.imgHeight))

    # convert to raw bytes
    raw_left = gray_left_resized.tobytes()
    raw_right = gray_right_resized.tobytes()
    # size sanity check
    height, width = gray_right_resized.shape
    print(f"Height: {height}, Width: {width}")

    # write to files
    path = 'generators/stereoacc/src/test/utils/intermediate/'
    with open(f'{path}left_matrix', 'wb') as f:
        f.write(raw_left)
    with open(f'{path}right_matrix', 'wb') as f:
        f.write(raw_right)
    with open('dimensions', 'w') as f:
        f.write(f"{height},{width},{args.min_disp},{args.max_disp},{args.block_size}")