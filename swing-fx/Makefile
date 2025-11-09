.PHONY: test repl clean help

test:
	clj -M:test -m clojure.test

repl:
	clj -M:dev:test

clean:
	rm -rf .cpcache

help:
	@echo "swing-fx library"
	@echo ""
	@echo "Available targets:"
	@echo "  make test   - Run tests"
	@echo "  make repl   - Start REPL"
	@echo "  make clean  - Clean build artifacts"
