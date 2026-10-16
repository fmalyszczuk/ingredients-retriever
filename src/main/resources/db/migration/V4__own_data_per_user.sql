-- Recipes and shopping list items belong to one user. Until now they were shared by everybody.
--
-- Rows that already exist have no owner, so they are handed to the oldest account (on a single-user installation that
-- is simply "your data"). If there are no accounts yet, those rows are unreachable anyway and are dropped.
-- Deleting a user deletes their data.
ALTER TABLE recipes ADD COLUMN user_id BIGINT;
ALTER TABLE shopping_list_items ADD COLUMN user_id BIGINT;

UPDATE recipes SET user_id = (SELECT min(id) FROM users);
UPDATE shopping_list_items SET user_id = (SELECT min(id) FROM users);
DELETE FROM recipes WHERE user_id IS NULL;
DELETE FROM shopping_list_items WHERE user_id IS NULL;

ALTER TABLE recipes ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE shopping_list_items ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE recipes ADD CONSTRAINT fk_recipes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE shopping_list_items ADD CONSTRAINT fk_shopping_list_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX ix_recipes_user_id ON recipes (user_id);

-- One row per item name *per user*, ignoring case.
DROP INDEX uq_shopping_list_items_name;
CREATE UNIQUE INDEX uq_shopping_list_items_user_name ON shopping_list_items (user_id, lower(name));
