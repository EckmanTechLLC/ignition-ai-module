# Architectural TODOs

## Conversation Compression/Summarization (CRITICAL)

**Problem:** Conversations hit 190K+ tokens (95% of 200K limit), causing Claude to hallucinate and skip tool calls.

**Current Limitation:**
- `MaxConversationHistoryMessages = 50` (message-based limit)
- No token counting before sending to Claude API
- Tool results can be huge (JSON responses)
- Conversations become unusable after 10-15 messages with heavy tool use

**Solution Options:**

### Option A: Token-Based Truncation (Quick Fix)
- Count actual tokens before sending to API
- Truncate to stay under threshold (e.g., 150K tokens)
- Drop oldest messages first
- **Pros:** Simple, immediate fix
- **Cons:** Loses context, still hits limits eventually

### Option B: Conversation Compression (Like Claude Code /compact)
- Summarize older messages periodically
- Keep recent messages intact
- Preserve key findings/data
- Drop old tool results entirely
- **Pros:** Conversations can continue indefinitely
- **Cons:** Complex to implement, requires careful design

### Option C: Hybrid Approach
- Token-based truncation for immediate relief
- Compression feature for long-term solution

## Design Decisions Needed:
1. When to compress? (auto threshold? user button? every N messages?)
2. What to preserve? (recent N messages? key findings? structured data?)
3. How to summarize? (call Claude API? template-based? keep only user questions?)
4. Storage? (compressed history in DB? show "history compressed" indicator?)

## Recommendation:
Start with Option A (token-based truncation) to prevent immediate breakage, then implement Option B for long-term usability.

## Related:
- UI already has "Clear Conversation" button (not sufficient)
- `showTokenUsage` prop exists - could add warning indicator
