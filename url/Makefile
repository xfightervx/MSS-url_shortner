.PHONY: run

psql := .first_run

run: $(psql)
	./mvnw spring-boot:run


clean:
	rm -f .first_run
	rm -f ./target/*.jar


debug :
	./mvnw -X spring-boot:run

$(psql):
	sudo -u postgres psql -f ./db_init.sql
	touch .first_run