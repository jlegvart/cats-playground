CREATE TABLE data (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  url VARCHAR(500) NOT NULL,
  content TEXT NOT NULL
);