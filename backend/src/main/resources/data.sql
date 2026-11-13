INSERT INTO users (email, password_hash, name, department, rating_avg, rating_count, is_active, email_verified, created_at, updated_at)
VALUES
  ('demo@campus.edu', 'seeded', 'Demo User', 'Sustainability', 4.8, 12, true, true, NOW(), NOW())
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO listings (user_id, title, description, category, item_condition, listing_type, price, status, created_at, updated_at)
SELECT u.id, v.title, v.description, v.category, v.item_condition, v.listing_type, v.price, v.status, NOW(), NOW()
FROM users u
JOIN (
  SELECT 'MacBook Pro 14"' AS title, '14-inch MacBook Pro, lightly used and ready for classes.' AS description, 'ELECTRONICS' AS category, 'LIKE_NEW' AS item_condition, 'SELL' AS listing_type, 70550.00 AS price, 'ACTIVE' AS status
  UNION ALL
  SELECT 'Levi''s Denim Jacket', 'Classic denim layer in great condition.', 'ACCESSORIES', 'GOOD', 'SELL', 2905.00, 'ACTIVE'
  UNION ALL
  SELECT 'Psychology Bundle', 'Core textbooks for psychology and research methods.', 'BOOKS', 'FAIR', 'SELL', 3320.00, 'ACTIVE'
  UNION ALL
  SELECT 'Nike Air Force 1', 'Clean pair with plenty of wear left.', 'ACCESSORIES', 'LIKE_NEW', 'SELL', 5395.00, 'ACTIVE'
) v ON 1=1
WHERE u.email = 'demo@campus.edu'
  AND NOT EXISTS (
    SELECT 1 FROM listings l
    WHERE l.user_id = u.id AND l.title = v.title
  );

INSERT INTO listing_images (listing_id, image_url)
SELECT l.id, v.image_url
FROM listings l
JOIN users u ON u.id = l.user_id
JOIN (
  SELECT 'MacBook Pro 14"' AS title, '/images/listing-macbook-pro-14.jpg' AS image_url
  UNION ALL
  SELECT 'Levi''s Denim Jacket', '/images/listing-levis-denim-jacket.jpg'
  UNION ALL
  SELECT 'Psychology Bundle', '/images/listing-psychology-bundle.jpg'
  UNION ALL
  SELECT 'Nike Air Force 1', '/images/listing-nike-air-force-1.jpg'
) v ON v.title = l.title
WHERE u.email = 'demo@campus.edu'
  AND NOT EXISTS (
    SELECT 1 FROM listing_images li
    WHERE li.listing_id = l.id AND li.image_url = v.image_url
  );

INSERT INTO wishlist_items (user_id, listing_id, created_at)
SELECT u.id, l.id, NOW()
FROM users u
JOIN listings l ON l.user_id = u.id
WHERE u.email = 'demo@campus.edu'
  AND l.title IN ('MacBook Pro 14"', 'Nike Air Force 1')
  AND NOT EXISTS (
    SELECT 1 FROM wishlist_items w
    WHERE w.user_id = u.id AND w.listing_id = l.id
  );
