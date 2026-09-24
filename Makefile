.PHONY: runners test

runners:
	docker build -t coderunner/cpp runners/cpp

test:
	cd backend && ./gradlew test