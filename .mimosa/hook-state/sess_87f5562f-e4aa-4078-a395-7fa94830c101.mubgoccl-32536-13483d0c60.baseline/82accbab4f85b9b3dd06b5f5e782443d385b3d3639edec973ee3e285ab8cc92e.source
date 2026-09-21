package config

import (
	"os"
	P "path"
	"strings"
	"testing"
)

func TestWriteFileRejectsOversize(t *testing.T) {
	dir := t.TempDir()
	file := P.Join(dir, "out.yaml")

	err := writeFile(file, strings.NewReader(strings.Repeat("a", maxConfigFileSize+1)))
	if err == nil {
		t.Fatal("expected oversize error")
	}
	if _, statErr := os.Stat(file); !os.IsNotExist(statErr) {
		t.Fatal("oversize file must not be committed")
	}

	// 原子替换：新内容更短时不能残留旧尾部
	if err := writeFile(file, strings.NewReader("new")); err != nil {
		t.Fatal(err)
	}
	if err := writeFile(file, strings.NewReader("x")); err != nil {
		t.Fatal(err)
	}
	content, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "x" {
		t.Fatalf("expected atomic replace, got %q", content)
	}
}
