ALTER TABLE spaces
    ADD COLUMN image_external_id UUID REFERENCES images(external_id);
