.PHONY: runners test

runners:
	docker build -t coderunner/cpp runners/cpp
	docker build -t coderunner/c runners/c
	docker build -t coderunner/java runners/java
	docker build -t coderunner/python runners/python
	docker build -t coderunner/js runners/js

test:
	cd backend && ./gradlew test

up:
	docker compose up --build

down:
	docker compose down