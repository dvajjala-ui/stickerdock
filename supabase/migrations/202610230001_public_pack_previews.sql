-- Allow optional, checksum-addressed public WebP pack previews alongside
-- existing ZIP archives and the catalog JSON. Leave any other configured
-- MIME types, limits and access settings untouched. Safe to apply twice.
update storage.buckets
set allowed_mime_types = array_append(allowed_mime_types, 'image/webp')
where id = 'sticker-factory-public'
  and allowed_mime_types is not null
  and not ('image/webp' = any(allowed_mime_types));
