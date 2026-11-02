import json
import unittest
from unittest.mock import patch

import trend_intelligence as trends

NOW = 1791936000  # 2026-10-14 00:00 UTC


class TrendIntelligenceTest(unittest.TestCase):
    def test_rss_and_owner_topics_make_category_plans_without_key(self):
        config = {"rating": "GENERAL", "content_categories": ["TRENDING", "INDIA"],
                  "topics": ["INDIA | Monsoon chai reactions"], "feeds": ["https://example.com/feed"]}
        source = [{"title": "Fresh technology event", "url": "https://example.com/story",
                   "published": "Wed, 14 Oct 2026 00:00:00 GMT"}]
        with patch.object(trends, "references", return_value=(source, [])):
            brief = trends.build_brief(config, NOW, key="")
        self.assertEqual(brief["source_mode"], "rss")
        self.assertEqual(brief["topic_plans"][0]["pack_category"], "HINGLISH")
        self.assertEqual(brief["topic_plans"][1]["pack_category"], "NEWS")
        self.assertTrue(any("GROQ_TRENDS_API_KEY" in item for item in brief["warnings"]))

    def test_browser_results_are_validated_and_preferred(self):
        raw = {"items": [{"category": "MOVIES", "title": "A new film opened",
                           "sticker_angle": "Opening weekend excitement",
                           "source_url": "https://example.com/film", "published_at": "2026-10-14"}]}
        with patch.object(trends, "references", return_value=([], [])), \
                patch.object(trends, "browser_research", return_value="research https://example.com/film"), \
                patch.object(trends, "structure_research", return_value=trends.validate_items(raw, ["MOVIES"])):
            brief = trends.build_brief({"rating": "GENERAL", "content_categories": ["MOVIES"],
                                        "topics": [], "feeds": []}, NOW, key="key")
        self.assertEqual(brief["source_mode"], "groq_browser_search")
        self.assertEqual(brief["topic_plans"][0]["pack_category"], "MOVIES")

    def test_adult_humor_requires_private_mature_run(self):
        general = trends.validate_categories(["EVERYDAY", "ADULT_HUMOR"], "GENERAL")
        self.assertEqual(general, ["EVERYDAY"])
        mature = trends.validate_categories(["ADULT_HUMOR"], "MATURE")
        self.assertEqual(mature, ["ADULT_HUMOR"])

    def test_untrusted_or_invalid_sources_are_rejected(self):
        invalid = {"items": [{"category": "TRENDING", "title": "Bad", "sticker_angle": "Bad",
                               "source_url": "http://user:pass@example.com", "published_at": "today"}]}
        with self.assertRaises(ValueError):
            trends.validate_items(invalid, ["TRENDING"])

    def test_custom_topic_prefix_must_be_enabled(self):
        with self.assertRaises(ValueError):
            trends.parse_custom_topic("POLITICS | Parliament reaction", ["CREATIVE"])

    def test_browser_failure_keeps_rss_and_reports_http_status(self):
        import urllib.error
        source = [{"title": "India event", "url": "https://example.com/india-event",
                   "published": "Wed, 14 Oct 2026 00:00:00 GMT"}]
        with patch.object(trends, "references", return_value=(source, [])), \
                patch.object(trends, "browser_research", side_effect=urllib.error.HTTPError(
                    "https://api.example.com", 429, "limited", {}, None)):
            brief = trends.build_brief({"rating": "GENERAL", "content_categories": ["TRENDING", "INDIA"],
                                        "topics": [], "feeds": ["https://example.com/feed"]}, NOW, key="key")
        self.assertEqual(brief["source_mode"], "rss")
        self.assertEqual(len(brief["topic_plans"]), 1)
        self.assertIn("Browser search HTTP 429; RSS/owner topics used", brief["warnings"])

    def test_india_trends_uses_recent_linked_news_without_copying_media(self):
        xml = b'''<rss xmlns:ht="https://trends.google.com/trending/rss"><channel>
          <item><title>Old trend</title><pubDate>Sun, 11 Oct 2026 00:00:00 GMT</pubDate>
            <ht:news_item><ht:news_item_title>Old story</ht:news_item_title><ht:news_item_url>https://news.example/old</ht:news_item_url></ht:news_item></item>
          <item><title>Cricket final</title><pubDate>Wed, 14 Oct 2026 00:00:00 GMT</pubDate>
            <ht:picture>https://images.example/third-party.jpg</ht:picture>
            <ht:news_item><ht:news_item_title>India final preview</ht:news_item_title><ht:news_item_url>https://news.example/final</ht:news_item_url></ht:news_item></item>
        </channel></rss>'''
        with patch.object(trends, "fetch_bytes", return_value=xml):
            items, warnings = trends.rss_items(["https://trends.google.com/trending/rss?geo=IN"], NOW, ["TRENDING", "INDIA"])
        self.assertEqual(warnings, [])
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["source_url"], "https://news.example/final")
        self.assertIn("Cricket final", items[0]["sticker_angle"])
        self.assertNotIn("picture", str(items))


if __name__ == "__main__":
    unittest.main()
