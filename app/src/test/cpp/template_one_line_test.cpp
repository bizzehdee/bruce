#include "template_one_line.h"

#include <gtest/gtest.h>

using bruce::oneLineToolJson;

TEST(TemplateOneLine, IndentedTojsonPrintsOnOneLine) {
    EXPECT_EQ("{{ t | tojson }}", oneLineToolJson("{{ t | tojson(indent=4) }}"));
    EXPECT_EQ("{{ t | tojson }}", oneLineToolJson("{{ t | tojson( indent = 2 ) }}"));
    EXPECT_EQ("{{ t|tojson}}{{ u|tojson }}", oneLineToolJson("{{ t|tojson(indent=4)}}{{ u|tojson (indent=4) }}"));
}

TEST(TemplateOneLine, EverythingElseIsLeftAlone) {
    EXPECT_EQ("{{ t | tojson }}", oneLineToolJson("{{ t | tojson }}"));
    EXPECT_EQ("{{ t | tojson(ensure_ascii=False) }}", oneLineToolJson("{{ t | tojson(ensure_ascii=False) }}"));
    EXPECT_EQ("{{ t | tojson(indent=4, ensure_ascii=False) }}", oneLineToolJson("{{ t | tojson(indent=4, ensure_ascii=False) }}"));
    EXPECT_EQ("{{ t | tojson(indent=x) }}", oneLineToolJson("{{ t | tojson(indent=x) }}"));
    EXPECT_EQ("{{ t | totojson(indent=4) }} tojsonify(indent=4)", oneLineToolJson("{{ t | totojson(indent=4) }} tojsonify(indent=4)"));
    EXPECT_EQ("tojson(indent=4", oneLineToolJson("tojson(indent=4"));
    EXPECT_EQ("", oneLineToolJson(""));
}
