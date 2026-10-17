#import "SafeWebPDecode.h"
@import SDWebImage;
@import SDWebImageWebPCoder;

UIImage *SDockDecodeWebPFirstFrame(NSData *data, CGSize thumbnailSize, NSUInteger limitBytes) {
    if (data.length == 0 || data.length > 500000) return nil;
    @try {
        NSMutableDictionary *options = [@{SDImageCoderDecodeFirstFrameOnly: @YES} mutableCopy];
        if (thumbnailSize.width > 0 && thumbnailSize.height > 0) {
            options[SDImageCoderDecodeThumbnailPixelSize] = [NSValue valueWithCGSize:thumbnailSize];
        }
        if (limitBytes > 0) options[SDImageCoderDecodeScaleDownLimitBytes] = @(limitBytes);
        return [SDImageWebPCoder.sharedCoder decodedImageWithData:data options:options];
    } @catch (NSException *exception) {
        // Do not log filenames, input pixels or exception text from private media.
        return nil;
    }
}
