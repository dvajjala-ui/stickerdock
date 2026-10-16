#import <UIKit/UIKit.h>

/// Defensive boundary for malformed imported media in an Objective-C codec.
FOUNDATION_EXPORT UIImage * _Nullable SDockDecodeWebPFirstFrame(NSData * _Nonnull data, CGSize thumbnailSize, NSUInteger limitBytes)
    NS_SWIFT_NAME(safeWebPFirstFrame(data:thumbnail:limitBytes:));
