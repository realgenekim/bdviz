DIR ?= .

run:
	BD_VIEWER_DIR=$(DIR) clj -J-Xdock:name="BD Viewer" -M -m bd-viewer.core 2>&1 | tee 00LOGS.txt

runtests-once:
	@echo "Checking compilation..."
	clj -M -e "(compile 'bd-viewer.core)"

# Start nREPL server (auto-assigns port, writes to .nrepl-port)
nrepl:
	clj -M:nrepl

# Configure MCP server in Claude Code
mcp-configure:
	claude mcp add clojure-mcp -- /bin/sh -c 'PORT=$$(cat $(shell pwd)/.nrepl-port); cd $(shell pwd) && clojure -X:mcp:dev :port $$PORT'

# Run MCP server (for testing)
mcp-run:
	@echo "🚀 Starting Clojure MCP server..."
	@echo "   Reading port from: $(shell pwd)/.nrepl-port"
	PORT=$$(cat $(shell pwd)/.nrepl-port); cd $(shell pwd) && clojure -X:mcp:dev :port $$PORT

# Clean compiled artifacts
clean:
	rm -rf .cpcache/ .nrepl-port

# Help
help:
	@echo "Available commands:"
	@echo "  make run                          - Run bd-viewer in current directory"
	@echo "  make run DIR=../slack-retriever   - Run bd-viewer for a different project"
	@echo "  make runtests-once                - Check compilation"
	@echo "  make nrepl                        - Start nREPL server (auto-port, writes to .nrepl-port)"
	@echo "  make mcp-configure                - Configure MCP server in Claude Code"
	@echo "  make mcp-run                      - Run MCP server (reads port from .nrepl-port)"
	@echo "  make clean                        - Clean compiled artifacts"
	@echo "  make help                         - Show this help"

.PHONY: run runtests-once nrepl mcp-configure mcp-run clean help