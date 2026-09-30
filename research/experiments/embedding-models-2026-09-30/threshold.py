"""Scores EmbeddingGemma gives right notes, other notes, and questions no note answers (TASK-097)."""
import importlib.util, json, os, sys
os.environ["PORT"] = os.environ.get("PORT", "8097")
sys.argv = [sys.argv[0], "embeddinggemma"]
spec = importlib.util.spec_from_file_location("ev", os.path.join(os.path.dirname(__file__), "eval.py"))
source = open(spec.origin).read().split("query_prefix, document_prefix = prefixes()")[0]
ns = {}
exec(source, ns)
q, d = ns["PREFIX"]["embeddinggemma"]
docs = ns["embed"]([d + p for p in ns["PASSAGES"]])
answered = ns["embed"]([q + x for x, _ in ns["QUERIES"]])
unanswered_questions = ["What is the capital of Mongolia?", "How do I change a car's timing belt?", "Who won the 1966 World Cup?", "What is my blood type?", "How many moons does Jupiter have?"]
unanswered = ns["embed"]([q + x for x in unanswered_questions])
right = [ns["cosine"](v, docs[t]) for v, (_, t) in zip(answered, ns["QUERIES"])]
other = [max(ns["cosine"](v, docs[i]) for i in range(len(docs)) if i != t) for v, (_, t) in zip(answered, ns["QUERIES"])]
none = [max(ns["cosine"](v, x) for x in docs) for v in unanswered]
print(json.dumps({"right_min": round(min(right), 3), "right_mean": round(sum(right) / len(right), 3), "best_other_max": round(max(other), 3), "unanswered_best": [round(x, 3) for x in none]}))
