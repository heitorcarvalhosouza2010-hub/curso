import unittest
from unittest.mock import Mock, patch

from src.ai_service import (
    AIServiceError,
    analyze_plate_image,
    chat_with_assistant,
    suggest_meals,
)


class AIServiceTests(unittest.TestCase):
    def test_photo_analysis_sends_image_and_returns_validated_estimate(self) -> None:
        response = Mock()
        response.json.return_value = {
            "choices": [
                {
                    "message": {
                        "content": (
                            '{"dish_name":"Prato com frango",'
                            '"foods":["frango","arroz","salada"],'
                            '"portion_g":350,"calories":520,'
                            '"protein_g":38,"carbs_g":52,"fat_g":14,'
                            '"confidence":"média",'
                            '"uncertainty":"O óleo e o peso são incertos.",'
                            '"recommendation":"Inclua variedade ao longo do dia."}'
                        )
                    }
                }
            ]
        }
        with patch("src.ai_service.requests.post", return_value=response) as post:
            analysis = analyze_plate_image(
                api_key="test-key",
                api_url="https://example.invalid/chat",
                model="vision-model",
                image=b"sample-image",
                mime_type="image/jpeg",
                goal="Ganhar massa muscular",
            )

        self.assertEqual(analysis["dish_name"], "Prato com frango")
        self.assertEqual(analysis["protein_g"], 38.0)
        payload = post.call_args.kwargs["json"]
        user_content = payload["messages"][1]["content"]
        self.assertIn("Ganhar massa muscular", user_content[0]["text"])
        self.assertTrue(
            user_content[1]["image_url"]["url"].startswith(
                "data:image/jpeg;base64,"
            )
        )

    def test_photo_analysis_rejects_invalid_result_and_oversized_image(self) -> None:
        response = Mock()
        response.json.return_value = {
            "choices": [{"message": {"content": '{"dish_name":"Sopa"}'}}]
        }
        with patch("src.ai_service.requests.post", return_value=response):
            with self.assertRaisesRegex(AIServiceError, "lista de alimentos"):
                analyze_plate_image(
                    api_key="test-key",
                    api_url="https://example.invalid/chat",
                    model="vision-model",
                    image=b"image",
                    mime_type="image/png",
                    goal="Manter o peso",
                )

        with patch("src.ai_service.requests.post") as post:
            with self.assertRaisesRegex(AIServiceError, "até 10 MB"):
                analyze_plate_image(
                    api_key="test-key",
                    api_url="https://example.invalid/chat",
                    model="vision-model",
                    image=b"x" * (10 * 1024 * 1024 + 1),
                    mime_type="image/png",
                    goal="Manter o peso",
                )
        post.assert_not_called()

    def test_photo_analysis_requires_api_key_without_network_call(self) -> None:
        with patch("src.ai_service.requests.post") as post:
            with self.assertRaisesRegex(AIServiceError, "AI_API_KEY"):
                analyze_plate_image(
                    api_key=None,
                    api_url="https://example.invalid/chat",
                    model="vision-model",
                    image=b"image",
                    mime_type="image/png",
                    goal="Manter o peso",
                )
        post.assert_not_called()

    def test_chat_requires_api_key_without_network_call(self) -> None:
        with patch("src.ai_service.requests.post") as post:
            with self.assertRaisesRegex(AIServiceError, "AI_API_KEY"):
                chat_with_assistant(
                    api_key=None,
                    api_url="https://example.invalid/chat",
                    model="test-model",
                    messages=[{"role": "user", "content": "Olá"}],
                )
        post.assert_not_called()

    def test_chat_sends_conversation_and_returns_answer(self) -> None:
        response = Mock()
        response.json.return_value = {
            "choices": [{"message": {"content": "Olá! Em que posso ajudar?"}}]
        }
        messages = [
            {"role": "user", "content": "O que é uma refeição equilibrada?"},
            {"role": "assistant", "content": "Em geral, pode incluir variedade."},
            {"role": "user", "content": "Pode dar um exemplo?"},
        ]
        with patch("src.ai_service.requests.post", return_value=response) as post:
            answer = chat_with_assistant(
                api_key="test-key",
                api_url="https://example.invalid/chat",
                model="test-model",
                messages=messages,
            )

        self.assertEqual(answer, "Olá! Em que posso ajudar?")
        payload = post.call_args.kwargs["json"]
        self.assertEqual(payload["messages"][-3:], messages)
        self.assertIn("diagnostique", payload["messages"][0]["content"])

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
