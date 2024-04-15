// This code is adapted from the following source:
// https://github.com/ankitdhall/stereo-block-matching/blob/master/sbm.cpp
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

typedef struct {
    int width;
    int height;
    unsigned char *data;
} Image;

typedef struct {
    int width;
    int height;
    signed char *data;
} Disp_Image;

void free_image(Image *image) {
    if (image) {
        free(image->data);
        free(image);
    }
}

void free_disp_image(Disp_Image *image) {
    if (image) {
        free(image->data);
        free(image);
    }
}

Image *load_image(const char *file_path) {
    FILE *file = fopen(file_path, "rb");
    if (!file) {
        printf("Error: Could not open file for reading\n");
        return NULL;
    }

    // Get the file size
    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);

    // Allocate memory for the image data
    unsigned char *data = (unsigned char *)malloc(file_size);
    if (!data) {
        printf("Error: Memory allocation failed\n");
        fclose(file);
        return NULL;
    }

    // Read the data from the file
    size_t bytes_read = fread(data, 1, file_size, file);
    if (bytes_read != file_size) {
        printf("Error: Could not read file\n");
        fclose(file);
        free(data);
        return NULL;
    }

    fclose(file);

    // Create an Image structure and populate its fields
    Image *image = (Image *)malloc(sizeof(Image));
    if (!image) {
        printf("Error: Memory allocation failed\n");
        free(data);
        return NULL;
    }

    FILE *dim_file = fopen("../../output/intermediate/dimensions", "r");
    if (!dim_file) {
        printf("Error: Could not open file for writing\n");
        free_image(image);
        return NULL;
    }
    if (fscanf(dim_file, "%d,%d", &image->height, &image->width) != 2) {
        printf("Error: Failed to read dimensions from file\n");
        fclose(file);
        return NULL;
    }
    // Set the image data pointer
    image->data = data;

    return image;
}


int square(char x) {
    return (int)x*(int)x;
}

// Core function computing stereoBM
Disp_Image* compute_dispartiy(Image *left, Image *right, int min_disparity, int max_disparity, int half_block_size) {
    // allocate data for disparity, use calloc for 0 initialization
    int SAD = 0;
    int min_SAD = INT32_MAX;
    int l_r, l_c, r_r, r_c;
    int height = left->height;
    int width = left->width;

    signed char *disparity = (signed char *)calloc(width*height, sizeof(signed char));
    if (!disparity) {
        printf("Error: Memory allocation failed\n");
        return NULL;
    }

    // compute disparity
    // outer loop iterating over blocks
    for (int i=0+half_block_size; i<height-half_block_size; i++) {
        for (int j=0+half_block_size-min_disparity; j<width-half_block_size-max_disparity; j++) {
            // middle loop per block
            min_SAD = INT32_MAX;
            for (int offset=min_disparity; offset<max_disparity; offset++) {
                SAD = 0;
                // inner loop per pixel: compute SAD
                for (l_r = i-half_block_size; l_r < half_block_size+i; l_r++) {
                    for (l_c = j-half_block_size; l_c < half_block_size+j; l_c++) {
                        r_r = l_r;
                        r_c = l_c + offset;
                        SAD += abs(left->data[l_r*width+l_c] - right->data[r_r*width+r_c]);
                    }
                }
                // reduction step
                if (SAD < min_SAD) {
                    min_SAD = SAD;
                    disparity[i*width+j+max_disparity] = offset;
                }
            }
        }
    }

    Disp_Image *disparity_image = (Disp_Image *)malloc(sizeof(Disp_Image));
    if (!disparity_image) {
        printf("Error: Memory allocation failed\n");
        free(disparity);
        return NULL;
    }
    disparity_image->width = width;
    disparity_image->height = height;
    disparity_image->data = disparity;
    return disparity_image;
}

int main() {
    const char *left_file_path = "intermediate/left_matrix";
    const char *right_file_path = "intermediate/left_matrix";
    FILE *dim_file = fopen("intermediate/dimensions", "r");
    if (!dim_file) {
        printf("Error: Could not open file for writing\n");
        return 1;
    }
    int height, width, min_disparity, max_disparity, block_size;
    if (fscanf(dim_file, "%d,%d,%d,%d,%d", &height, &width, &min_disparity, &max_disparity, &block_size) != 5) {
        printf("Error: Failed to read dimensions from file\n");
        fclose(dim_file);
        return 1;
    }
    fclose(dim_file);
    Image *left_image = load_image(left_file_path);
    if (!left_image) {
        return 1;
    }
    Image *right_image = load_image(right_file_path);
    if (!right_image) {
        free_image(left_image);
        return 1;
    }
    Disp_Image *disparity_image = compute_dispartiy(left_image, right_image, min_disparity, max_disparity, block_size/2);
    // Save the disparity image
    FILE *file = fopen("intermediate/c_references", "wb");
    if (!file) {
        printf("Error: Could not open file for writing\n");
        free_image(left_image);
        free_image(right_image);
        free_disp_image(disparity_image);
        return 1;
    }
    // write only the data
    fwrite(disparity_image->data, 1, disparity_image->width*disparity_image->height, file);
    fclose(file);
    free_image(left_image);
    free_image(right_image);
    free_disp_image(disparity_image);
    return 0;
}