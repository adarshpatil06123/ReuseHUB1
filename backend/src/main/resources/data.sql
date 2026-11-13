DELETE w
FROM wishlist_items w
JOIN listings l ON w.listing_id = l.id
JOIN users u ON l.user_id = u.id
WHERE u.email = 'demo@campus.edu'
  AND (
    (l.title = 'MacBook Pro 14"' AND l.description = '14-inch MacBook Pro, lightly used and ready for classes.')
    OR (l.title = 'Levi''s Denim Jacket' AND l.description = 'Classic denim layer in great condition.')
    OR (l.title = 'Psychology Bundle' AND l.description = 'Core textbooks for psychology and research methods.')
    OR (l.title = 'Nike Air Force 1' AND l.description = 'Clean pair with plenty of wear left.')
  );

UPDATE listings l
JOIN users u ON l.user_id = u.id
SET l.status = 'SOLD'
WHERE u.email = 'demo@campus.edu'
  AND (
    (l.title = 'MacBook Pro 14"' AND l.description = '14-inch MacBook Pro, lightly used and ready for classes.')
    OR (l.title = 'Levi''s Denim Jacket' AND l.description = 'Classic denim layer in great condition.')
    OR (l.title = 'Psychology Bundle' AND l.description = 'Core textbooks for psychology and research methods.')
    OR (l.title = 'Nike Air Force 1' AND l.description = 'Clean pair with plenty of wear left.')
  );
