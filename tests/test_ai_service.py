import unittest
from unittest.mock import Mock, patch

from src.ai_service import AIServiceError, suggest_meals


class AIServiceTests(unittest.TestCase):
    def test_missing_api_key_fails_without_network_call(self) -> None:
        with patch("src.ai_service.requests.post") as post:
            with self.assertRaisesRegex(AIServiceError, "AI_API_KEY"):
                suggest_meals(
                    api_key=None,
                    api_url="https://example.invalid/chat",
                    model="test-model",
                    summary={},
                )
        post.assert_not_called()

    def test_suggestion_uses_only_summary_and_returns_text(self) -> None:
        response = Mock()
        response.json.return_value = {
            "choices": [{"message": {"content": "Experimente frutas e iogurte."}}]
        }
        with patch("src.ai_service.requests.post", return_value=response) as post:
            suggestion = suggest_meals(
                api_key="test-key",
                api_url="https://example.invalid/chat",
                model="test-model",
                summary={"meal_count": 1, "calories": 300},
            )

        self.assertEqual(suggestion, "Experimente frutas e iogurte.")
        payload = post.call_args.kwargs["json"]
        self.assertIn("calories", payload["messages"][1]["content"])
        self.assertNotIn("name", payload["messages"][1]["content"])


if __name__ == "__main__":
    unittest.main()
