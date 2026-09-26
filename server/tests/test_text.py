from app.services import rules
from app.text import clean_sentence, key, normalize


def test_normalize():
    assert normalize("يك  كتاب") == "یک کتاب"
    assert key("می‌خوام") == key("میخوام")
    assert normalize("١٢") == "۱۲"


def test_clean_sentence():
    assert clean_sentence("1. «آب می‌خوام»") == "آب می‌خوام"
    assert clean_sentence("- سرم درد می‌کنه.") == "سرم درد می‌کنه."


def test_rules():
    assert rules.classify("آب می‌خوای؟") == ("yes_no", "needs", 0.7)
    assert rules.classify("کجات درد می‌کنه؟")[:2] == ("choice", "pain")
    assert rules.classify("حالت چطوره؟")[:2] == ("choice", "feelings")
