create user mss_admin;
create database mss_db;
using database mss_db;
grant connect on database mss_db to mss_admin;
grant pg_read_all_data to mss_admin;
grant pg_write_all_data to mss_admin;