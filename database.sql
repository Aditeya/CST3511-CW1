DROP TABLE users;

CREATE TABLE users (
	email CHAR(40) NOT NULL PRIMARY KEY,
	password CHAR(8) NOT NULL,
	statecode CHAR(32),
	token VARCHAR(100)
);

INSERT INTO users(email, password) VALUES ('johndoe@email.com', 'password');

