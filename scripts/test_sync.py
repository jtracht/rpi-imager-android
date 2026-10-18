import unittest
from sync_upstream import android_tag, choose_release


def release(tag, date, identifier, **kwargs):
    return dict(tag_name=tag, published_at=date, id=identifier, draft=False, prerelease=False) | kwargs


class ReleaseSelectionTest(unittest.TestCase):
    def test_does_not_skip_releases_between_polls(self):
        releases = [release("v3", "2026-10-03", 3), release("v1", "2026-10-01", 1), release("v2", "2026-10-02", 2)]
        self.assertEqual("v2", choose_release(releases, "v1")["tag_name"])

    def test_ignores_drafts_and_prereleases(self):
        releases = [release("v1", "2026-10-01", 1), release("v2", "2026-10-02", 2, prerelease=True), release("v3", "2026-10-03", 3, draft=True)]
        self.assertEqual("v1", choose_release(releases, "v1")["tag_name"])

    def test_unknown_baseline_requires_review(self):
        with self.assertRaises(RuntimeError):
            choose_release([], "v1")

    def test_rejects_tag_injection(self):
        for tag in ["--upload-pack=bad", "a\nb", "../main", ""]:
            with self.assertRaises(ValueError):
                android_tag(tag)
        self.assertEqual("upstream/v2.0.12-android.1", android_tag("v2.0.12"))


if __name__ == "__main__":
    unittest.main()
