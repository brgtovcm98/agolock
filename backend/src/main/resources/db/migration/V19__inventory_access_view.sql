-- Inventory 위치를 한 번만 해석하는 단일 진실 view.
-- Box 재고는 Box가 속한 Shelf와 Room까지 거슬러 올라가 effective 위치를 만든다.
CREATE VIEW inventory_access AS
SELECT
    i.id AS inventory_id,
    i.space_id,
    i.item_id,
    i.room_id,
    i.shelf_id,
    i.box_id,
    i.status,
    i.version,
    i.name_override,
    i.description_override,
    i.category_id_override,
    i.price_amount_override,
    i.created_at,
    i.updated_at,
    COALESCE(i.room_id, s.room_id, bs.room_id) AS effective_room_id,
    COALESCE(i.shelf_id, b.shelf_id) AS effective_shelf_id,
    CASE
        WHEN i.box_id IS NOT NULL THEN 'BOX'
        WHEN i.shelf_id IS NOT NULL THEN 'SHELF'
        ELSE 'ROOM'
    END AS location_type
FROM inventory i
LEFT JOIN shelf s
    ON s.id = i.shelf_id
    AND s.space_id = i.space_id
LEFT JOIN box b
    ON b.id = i.box_id
    AND b.space_id = i.space_id
LEFT JOIN shelf bs
    ON bs.id = b.shelf_id
    AND bs.space_id = i.space_id
WHERE i.deleted_at IS NULL;
