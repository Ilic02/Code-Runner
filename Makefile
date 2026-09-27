.PHONY: runners test

runners:
	docker build -t coderunner/cpp runners/cpp
	docker build -t coderunner/c runners/c
	docker build -t coderunner/java runners/java

test:
	cd backend && ./gradlew test