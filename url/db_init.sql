revoke all privileges on database mss_db from mss_admin;
drop user if exists mss_admin;
CREATE USER mss_admin WITH PASSWORD '0';

Drop DATABASE IF EXISTS mss_db;
CREATE DATABASE mss_db;

GRANT CONNECT ON DATABASE mss_db TO mss_admin;

\c mss_db

GRANT pg_read_all_data TO mss_admin;
GRANT pg_write_all_data TO mss_admin;