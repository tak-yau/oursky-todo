#!/bin/bash
# Seed script: creates development data with proper nested subtasks
# Usage: cd backend && ./seed.sh

set -e

BASE_URL="http://localhost:8080"

# Global subtask ID counter (starts at 1, increments with each creation)
SUBTASK_ID=0

create_todo() {
  local title="$1"
  curl -s -X POST "$BASE_URL/api/todos" -H "Content-Type: application/json" -d "{\"title\":\"$title\"}" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['id'])"
}

add_subtask() {
  local todo_id="$1"
  local title="$2"
  local parent_id="${3:-null}"
  SUBTASK_ID=$((SUBTASK_ID + 1))
  curl -s -X POST "$BASE_URL/api/todos/$todo_id/subtasks" -H "Content-Type: application/json" -d "{\"subtaskTitle\":\"$title\",\"parentId\":$parent_id}" > /dev/null
  echo $SUBTASK_ID
}

update_subtask() {
  local todo_id="$1"
  local subtask_id="$2"
  local completed="$3"
  curl -s -X PUT "$BASE_URL/api/todos/$todo_id/subtasks/$subtask_id" -H "Content-Type: application/json" -d "{\"completed\":$completed}" > /dev/null
}

echo "Creating todos..."

# Todo 1: Build a fantastic product
TODO1=$(create_todo "Build a fantastic product")
echo "  Created todo $TODO1: Build a fantastic product"

S1_1=$(add_subtask $TODO1 "Define Product Vision, Market, and User Needs")
S1_2=$(add_subtask $TODO1 "Design, Prototype, and Plan the Solution")
S1_3=$(add_subtask $TODO1 "Test, Refine, and Ensure Quality Assurance")
S1_4=$(add_subtask $TODO1 "Develop and Implement Core Product Features")
S1_5=$(add_subtask $TODO1 "Launch, Market, and Iterate Based on User Feedback")

update_subtask $TODO1 $S1_1 true

# Nested under S1_2
add_subtask $TODO1 "Define and document functional requirements and user stories" $S1_2 > /dev/null
add_subtask $TODO1 "Create wireframes and user flow diagrams for key interactions" $S1_2 > /dev/null
add_subtask $TODO1 "Design high-fidelity mockups for core screens and UI elements" $S1_2 > /dev/null
add_subtask $TODO1 "Develop an interactive prototype for user testing and feedback" $S1_2 > /dev/null
add_subtask $TODO1 "Outline the technical architecture and implementation plan" $S1_2 > /dev/null

# Todo 2: Throw a celebration party
TODO2=$(create_todo "Throw a celebration party")
echo "  Created todo $TODO2: Throw a celebration party"

S2_1=$(add_subtask $TODO2 "Initial Planning & Budgeting")
S2_2=$(add_subtask $TODO2 "Venue & Vendor Booking")
S2_3=$(add_subtask $TODO2 "Guest Management & Invitations")
S2_4=$(add_subtask $TODO2 "Event Setup & Execution")
S2_5=$(add_subtask $TODO2 "Post-Party Wrap-up")

# Nested under S2_1
S2_1_1=$(add_subtask $TODO2 "Define project scope, goals, and key deliverables" $S2_1)
S2_1_2=$(add_subtask $TODO2 "Identify all required resources (personnel, tools, materials, services)" $S2_1)
S2_1_3=$(add_subtask $TODO2 "Research and estimate costs for each identified resource and activity" $S2_1)
S2_1_4=$(add_subtask $TODO2 "Develop a preliminary budget proposal with cost breakdowns" $S2_1)
S2_1_5=$(add_subtask $TODO2 "Establish initial budget approval process and funding sources" $S2_1)

# 3rd level under S2_1_2
add_subtask $TODO2 "List all necessary roles and specific skillsets required for the task." $S2_1_2 > /dev/null
add_subtask $TODO2 "Document all physical materials, consumables, and relevant data sources." $S2_1_2 > /dev/null
add_subtask $TODO2 "Catalog all specific software, hardware, and specialized equipment needed." $S2_1_2 > /dev/null
add_subtask $TODO2 "Identify any external vendors, internal support teams, or subscriptions required." $S2_1_2 > /dev/null
add_subtask $TODO2 "Estimate quantities and potential costs for each identified resource." $S2_1_2 > /dev/null

# Todo 3: Research latest AI technologies on vibe coding
TODO3=$(create_todo "Research latest AI technologies on vibe coding")
echo "  Created todo $TODO3: Research latest AI technologies on vibe coding"

S3_1=$(add_subtask $TODO3 "Define and Understand 'Vibe Coding' Concept")
S3_2=$(add_subtask $TODO3 "Identify Relevant AI")

# Nested under S3_1
S3_1_1=$(add_subtask $TODO3 "Search for existing definitions and discussions of 'Vibe Coding' online." $S3_1)
S3_1_2=$(add_subtask $TODO3 "Identify the core principles and characteristics commonly associated with 'Vibe Coding'." $S3_1)
add_subtask $TODO3 "Analyze the motivations and benefits developers seek from adopting 'Vibe Coding'." $S3_1 > /dev/null
add_subtask $TODO3 "Find practical examples or scenarios where 'Vibe Coding' is applied." $S3_1 > /dev/null
add_subtask $TODO3 "Synthesize findings into a concise working definition of 'Vibe Coding'." $S3_1 > /dev/null

# 3rd level under S3_1_2
add_subtask $TODO3 "Search for articles, blog posts, and forum discussions defining 'Vibe Coding'" $S3_1_2 > /dev/null

echo ""
echo "Seed data created successfully!"
