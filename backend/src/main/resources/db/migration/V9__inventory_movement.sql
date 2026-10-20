CREATE TABLE inventory_movement (
    id uuid PRIMARY KEY,
    space_id uuid NOT NULL REFERENCES space (id),
    inventory_id uuid NOT NULL,
    from_room_id uuid,
    from_shelf_id uuid,
    from_box_id uuid,
    to_room_id uuid,
    to_shelf_id uuid,
    to_box_id uuid,
    moved_by uuid NOT NULL REFERENCES app_user (id),
    moved_at timestamptz NOT NULL,
    CONSTRAINT ck_movement_from_one CHECK (num_nonnulls(from_room_id, from_shelf_id, from_box_id) = 1),
    CONSTRAINT ck_movement_to_one CHECK (num_nonnulls(to_room_id, to_shelf_id, to_box_id) = 1),
    CONSTRAINT fk_movement_inventory_space FOREIGN KEY (inventory_id, space_id) REFERENCES inventory (id, space_id),
    CONSTRAINT fk_movement_from_room_space FOREIGN KEY (from_room_id, space_id) REFERENCES room (id, space_id),
    CONSTRAINT fk_movement_from_shelf_space FOREIGN KEY (from_shelf_id, space_id) REFERENCES shelf (id, space_id),
    CONSTRAINT fk_movement_from_box_space FOREIGN KEY (from_box_id, space_id) REFERENCES box (id, space_id),
    CONSTRAINT fk_movement_to_room_space FOREIGN KEY (to_room_id, space_id) REFERENCES room (id, space_id),
    CONSTRAINT fk_movement_to_shelf_space FOREIGN KEY (to_shelf_id, space_id) REFERENCES shelf (id, space_id),
    CONSTRAINT fk_movement_to_box_space FOREIGN KEY (to_box_id, space_id) REFERENCES box (id, space_id)
);

CREATE INDEX ix_movement_page ON inventory_movement (space_id, inventory_id, moved_at DESC, id DESC);
