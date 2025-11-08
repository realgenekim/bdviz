run:
	clj -J-Xdock:icon=icons/icon.png -J-Xdock:name="BD Viewer" -M -m bd-viewer.core

runtests-once:
	@echo "Checking compilation..."
	clj -M -e "(compile 'bd-viewer.core)"

# Start nREPL server (auto-assigns port, writes to .nrepl-port)
nrepl:
	clj -M:nrepl

# Configure MCP server in Claude Code
mcp-configure:
	claude mcp add clojure-mcp -- /bin/sh -c 'PORT=$$(cat /Users/genekim/src.local/bd-viewer/.nrepl-port); cd /Users/genekim/src.local/bd-viewer && clojure -X:mcp :port $$PORT'

# Run MCP server (for testing)
run-mcp:
	PORT=$$(cat /Users/genekim/src.local/bd-viewer/.nrepl-port); cd /Users/genekim/src.local/bd-viewer && clojure -X:mcp :port $$PORT

# Clean compiled artifacts
clean:
	rm -rf .cpcache/ .nrepl-port target/

# Help
help:
	@echo "Available commands:"
	@echo "  make run           - Run bd-viewer"
	@echo "  make runtests-once - Check compilation"
	@echo "  make nrepl         - Start nREPL server (auto-port, writes to .nrepl-port)"
	@echo "  make mcp-configure - Configure MCP server in Claude Code"
	@echo "  make run-mcp       - Run MCP server (reads port from .nrepl-port)"
	@echo "  make clean         - Clean compiled artifacts"
	@echo "  make help          - Show this help"

.PHONY: run runtests-once nrepl mcp-configure run-mcp clean help
