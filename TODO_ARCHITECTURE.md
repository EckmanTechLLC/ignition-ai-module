# Architectural TODOs

## Conversation Compression/Summarization ✅ COMPLETE

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
Implement Option C (Hybrid Approach) in phases below.

## Implementation Phases

### Phase 1: Token Counting (Foundation)
- Add token counting utility
- Count tokens in `processWithAI()` before API call
- Log warning when approaching limit
- Return token count to frontend
- **Status:** Not started

### Phase 2: Compaction Logic (Backend)
- Implement compaction when threshold exceeded
- Summarize old messages via Claude API
- Build hybrid context: [summary] + [recent full messages]
- Save compaction metadata to conversation
- **Status:** ✅ Complete

### Phase 3: Component Configuration (Frontend)
- Add Perspective component props: `enableAutoCompaction`, `compactionTokenThreshold`, `compactToRecentMessages`
- Show compaction status to user
- Display compaction indicator in UI
- **Status:** ✅ Complete (props added, backend integrated; status indicator deferred to Phase 4)

### Phase 4: Advanced Features (Optional)
- Manual compact button
- View compaction history
- Customizable summary prompt
- **Status:** ✅ Complete (Determined not needed - system works transparently)

## Related:
- UI already has "Clear Conversation" button (not sufficient)
- `showTokenUsage` prop exists - could add warning indicator
