import unittest
from KeyValueStore import Create, Insert, Delete

# Need to complete the tests 

class TestCreate(unittest.TestCase):

    def setUp(self):
        self.store = Create()

    def test_create(self):
        # Test the create function
        store = Create()
        resulting_dictionary = {}
        self.assertDictEqual(store, resulting_dictionary)

    def test_insert(self):
        # Test the insert function
        answer = {'test': 13}
        Insert(self.store)
        self.assertDictEqual(self.store, answer)