"""
Ignition AI Module Backend Test Script
Run this in the Ignition Script Console to test the backend logic
independently of the Perspective component and routing.

Usage:
1. Set your API key and model name below
2. Run in Script Console
3. Check output for success/errors
"""

from java.net import HttpURLConnection, URL
from java.io import BufferedReader, InputStreamReader, OutputStreamWriter
from java.util import UUID
import system
import json

# =============================================================================
# CONFIGURATION - Set these values
# =============================================================================
API_KEY = "YOUR_API_KEY_HERE"  # Replace with your Claude API key
MODEL_NAME = "claude-sonnet-4-5-20250929"  # Or your preferred model
DATABASE_CONNECTION = "AIMEE_V3"  # Your database connection name
PROJECT_NAME = "aimee"  # Project to analyze
USER_NAME = "test_user"  # Test user name
TEST_MESSAGE = "What is this project about?"  # Test message

# =============================================================================
# Helper Functions
# =============================================================================

def makeClaudeAPICall(apiKey, modelName, systemPrompt, messages, tools):
	"""
	Make a call to the Claude API.
	Returns: (success, response_dict, error_message)
	"""
	try:
		# Build request using Python dicts
		requestBody = {
			"model": modelName,
			"max_tokens": 4096,
			"system": systemPrompt,
			"messages": messages,
			"tools": tools
		}

		# Convert to JSON string
		requestJson = json.dumps(requestBody)

		# Make HTTP request
		url = URL("https://api.anthropic.com/v1/messages")
		conn = url.openConnection()
		conn.setRequestMethod("POST")
		conn.setRequestProperty("Content-Type", "application/json")
		conn.setRequestProperty("x-api-key", apiKey)
		conn.setRequestProperty("anthropic-version", "2023-06-01")
		conn.setDoOutput(True)

		# Write request body
		writer = OutputStreamWriter(conn.getOutputStream())
		writer.write(requestJson)
		writer.flush()
		writer.close()

		# Read response
		responseCode = conn.getResponseCode()

		if responseCode == 200:
			reader = BufferedReader(InputStreamReader(conn.getInputStream()))
			response = ""
			line = reader.readLine()
			while line is not None:
				response += line
				line = reader.readLine()
			reader.close()

			# Parse JSON response using Python's json module
			responseDict = json.loads(response)
			return (True, responseDict, None)
		else:
			errorReader = BufferedReader(InputStreamReader(conn.getErrorStream()))
			errorResponse = ""
			line = errorReader.readLine()
			while line is not None:
				errorResponse += line
				line = errorReader.readLine()
			errorReader.close()

			return (False, None, "HTTP %d: %s" % (responseCode, errorResponse))

	except Exception as e:
		return (False, None, "Exception: %s" % str(e))

def testDatabaseConnection(dbConnection):
	"""
	Test if the database connection works and tables exist.
	Returns: (success, error_message)
	"""
	try:
		# Test query on conversations table
		query = "SELECT COUNT(*) as count FROM iai_conversations"
		result = system.db.runQuery(query, database=dbConnection)

		if result is not None:
			print("  ✓ iai_conversations table exists")

		# Test query on messages table
		query = "SELECT COUNT(*) as count FROM iai_messages"
		result = system.db.runQuery(query, database=dbConnection)

		if result is not None:
			print("  ✓ iai_messages table exists")

		return (True, None)

	except Exception as e:
		return (False, "Database error: %s" % str(e))

def createTestConversation(dbConnection, userName, projectName):
	"""
	Create a test conversation in the database.
	Returns: (success, conversation_id, error_message)
	"""
	try:
		conversationId = str(UUID.randomUUID())
		currentTime = system.date.now().getTime()

		query = """
		INSERT INTO iai_conversations
		(id, user_name, project_name, title, created_at, last_updated_at)
		VALUES (?, ?, ?, ?, ?, ?)
		"""

		system.db.runPrepUpdate(
			query,
			[conversationId, userName, projectName, "Test Conversation", currentTime, currentTime],
			database=dbConnection
		)

		print("  ✓ Created conversation: %s" % conversationId)
		return (True, conversationId, None)

	except Exception as e:
		return (False, None, "Database error: %s" % str(e))

def saveMessage(dbConnection, conversationId, role, content, inputTokens=None, outputTokens=None):
	"""
	Save a message to the database.
	Returns: (success, message_id, error_message)
	"""
	try:
		messageId = str(UUID.randomUUID())
		currentTime = system.date.now().getTime()

		query = """
		INSERT INTO iai_messages
		(id, conversation_id, role, content, input_tokens, output_tokens, timestamp)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		"""

		system.db.runPrepUpdate(
			query,
			[messageId, conversationId, role, content, inputTokens, outputTokens, currentTime],
			database=dbConnection
		)

		print("  ✓ Saved %s message: %s" % (role, messageId))
		return (True, messageId, None)

	except Exception as e:
		return (False, None, "Database error: %s" % str(e))

# =============================================================================
# Main Test Execution
# =============================================================================

def runTests():
	"""
	Run all backend tests.
	"""
	print("=" * 80)
	print("Ignition AI Module - Backend Test")
	print("=" * 80)
	print("")

	# Validate configuration
	print("[1/5] Validating configuration...")
	if API_KEY == "YOUR_API_KEY_HERE":
		print("  ✗ ERROR: Please set your API_KEY in the script")
		return
	print("  ✓ API Key: %s..." % API_KEY[:20])
	print("  ✓ Model: %s" % MODEL_NAME)
	print("  ✓ Database: %s" % DATABASE_CONNECTION)
	print("")

	# Test database connection
	print("[2/5] Testing database connection...")
	success, error = testDatabaseConnection(DATABASE_CONNECTION)
	if not success:
		print("  ✗ ERROR: %s" % error)
		return
	print("")

	# Create test conversation
	print("[3/5] Creating test conversation...")
	success, conversationId, error = createTestConversation(DATABASE_CONNECTION, USER_NAME, PROJECT_NAME)
	if not success:
		print("  ✗ ERROR: %s" % error)
		return
	print("")

	# Save user message
	print("[4/5] Saving user message...")
	success, messageId, error = saveMessage(DATABASE_CONNECTION, conversationId, "user", TEST_MESSAGE)
	if not success:
		print("  ✗ ERROR: %s" % error)
		return
	print("")

	# Test Claude API call
	print("[5/5] Testing Claude API call...")

	systemPrompt = """You are Ignition AI, an AI assistant for Inductive Automation's Ignition SCADA platform.
You help users understand their Ignition projects. Be concise and helpful."""

	messages = [
		{"role": "user", "content": TEST_MESSAGE}
	]

	# Simple test tool
	tools = [
		{
			"name": "get_project_info",
			"description": "Get information about the Ignition project",
			"input_schema": {
				"type": "object",
				"properties": {
					"project_name": {
						"type": "string",
						"description": "Name of the project"
					}
				},
				"required": ["project_name"]
			}
		}
	]

	print("  → Calling Claude API...")
	success, response, error = makeClaudeAPICall(API_KEY, MODEL_NAME, systemPrompt, messages, tools)

	if not success:
		print("  ✗ ERROR: %s" % error)
		return

	print("  ✓ API call successful!")

	# Parse response - now just Python dicts
	assistantText = ""
	content = response.get("content", [])
	usage = response.get("usage", {})

	if usage:
		inputTokens = usage.get("input_tokens", 0)
		outputTokens = usage.get("output_tokens", 0)
		print("  ✓ Token usage: %d input / %d output" % (inputTokens, outputTokens))

	for block in content:
		blockType = block.get("type")

		if blockType == "text":
			text = block.get("text", "")
			assistantText += text
			print("  ✓ Response text: %s..." % text[:100])
		elif blockType == "tool_use":
			toolName = block.get("name", "unknown")
			print("  ✓ Tool requested: %s" % toolName)

		# Save assistant response
		if assistantText:
			success, msgId, error = saveMessage(
				DATABASE_CONNECTION,
				conversationId,
				"assistant",
				assistantText,
				inputTokens,
				outputTokens
			)
			if not success:
				print("  ✗ ERROR saving response: %s" % error)

	print("")
	print("=" * 80)
	print("✓ ALL TESTS PASSED!")
	print("=" * 80)
	print("")
	print("Test conversation ID: %s" % conversationId)
	print("")
	print("You can view the conversation in the database:")
	print("  SELECT * FROM iai_conversations WHERE id = '%s'" % conversationId)
	print("  SELECT * FROM iai_messages WHERE conversation_id = '%s'" % conversationId)
	print("")

# Run the tests
runTests()
