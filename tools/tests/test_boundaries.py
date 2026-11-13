import unittest

from tools.boundaries import Query, fold, parse_javap, parse_query, shape

JAVAP = """Compiled from "Sky.java"
public class net.minecraft.client.Sky<T> extends java.lang.Object implements net.minecraft.client.Layer {
  private final int width;

  public net.minecraft.client.Sky(int);
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V

  public void draw(net.minecraft.client.Layer, java.util.List<java.lang.String>) throws java.io.IOException;
    Code:
       0: aload_1
       1: invokeinterface #7,  1            // InterfaceMethod net/minecraft/client/Layer.draw:()V
       6: getstatic     #9                  // Field com/mojang/blaze3d/systems/RenderSystem.fog:I

  public void draw(org.joml.Matrix4f);
    Code:
       0: return
}
"""


class QueryTest(unittest.TestCase):
    def test_a_comment_or_an_empty_line_is_no_query(self):
        self.assertIsNone(parse_query("# Atlas sprites"))
        self.assertIsNone(parse_query(""))

    def test_the_parts_of_a_line(self):
        self.assertEqual(Query(("a.B",), "", ""), parse_query("a.B"))
        self.assertEqual(Query(("a.B", "a.C"), "load", ""), parse_query("a.B|a.C#load"))
        self.assertEqual(Query(("a.B",), "load", 'Sprite."<init>"'), parse_query('a.B#load>Sprite."<init>"'))

    def test_the_constructor_name_is_not_the_start_of_the_text(self):
        self.assertEqual(Query(("a.B",), "<init>", ""), parse_query("a.B#<init>"))
        self.assertEqual(Query(("a.B",), "<init>", "a.C.size"), parse_query("a.B#<init>>a.C.size"))


class ShapeTest(unittest.TestCase):
    def setUp(self):
        self.disassembly = parse_javap(JAVAP)

    def shape(self, row: str) -> str:
        return shape(parse_query(row), self.disassembly)

    def test_a_class(self):
        self.assertEqual("class", self.shape("net.minecraft.client.Sky"))
        self.assertEqual("no class", self.shape("net.minecraft.client.Clouds"))

    def test_the_first_class_that_exists_is_named(self):
        self.assertEqual("client.Sky: class", self.shape("net.minecraft.client.Clouds|net.minecraft.client.Sky"))

    def test_the_declarations_of_a_member_have_short_type_names(self):
        self.assertEqual("private final int width", self.shape("net.minecraft.client.Sky#width"))
        self.assertEqual("public Sky(int)", self.shape("net.minecraft.client.Sky#<init>"))
        self.assertEqual("public void draw(Layer, List<String>) / public void draw(joml.Matrix4f)", self.shape("net.minecraft.client.Sky#draw"))
        self.assertEqual("no member", self.shape("net.minecraft.client.Sky#tick"))

    def test_the_instructions_that_name_a_text_are_counted(self):
        self.assertEqual("1", self.shape("net.minecraft.client.Sky#draw>Layer.draw"))
        self.assertEqual("1", self.shape("net.minecraft.client.Sky#draw>RenderSystem.fog"))
        self.assertEqual("0", self.shape("net.minecraft.client.Sky#draw>Layer.tick"))
        self.assertEqual("1", self.shape('net.minecraft.client.Sky#<init>>Object."<init>"'))


class FoldTest(unittest.TestCase):
    def test_versions_with_the_same_shape_are_one_range(self):
        self.assertEqual(
            ["    1.20 to 1.21: a", "    26.1: b", "    26.2: a"],
            fold(["1.20", "1.20.4", "1.21", "26.1", "26.2"], ["a", "a", "a", "b", "a"]),
        )
