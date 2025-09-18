# Test der neuen DOCX-Formatierung

## Überschriften-Test

### H3 Überschrift
#### H4 Überschrift
##### H5 Überschrift
###### H6 Überschrift

## Textformatierung

**Fetter Text** und *kursiver Text* funktionieren jetzt!

**Fett und *kombiniert* mit kursiv** ist auch möglich.

## Links

Hier ist ein [Link zu Google](https://www.google.com) und ein [interner Link](#test).

## Blockquotes

> Dies ist ein Blockquote.
> Es sollte eingerückt und kursiv dargestellt werden.

## Listen

- Erste Aufzählung
- Zweite Aufzählung
  - Verschachtelte Aufzählung
  - Noch eine verschachtelte
- Dritte Aufzählung

1. Nummerierte Liste
2. Zweiter Punkt
   1. Verschachtelte Nummerierung
   2. Noch eine verschachtelte
3. Dritter Punkt

## Tabellen

| Spalte 1 | Spalte 2 | Spalte 3 |
|----------|----------|----------|
| Zeile 1  | Daten 1  | Info 1   |
| Zeile 2  | Daten 2  | Info 2   |
| Zeile 3  | Daten 3  | Info 3   |

## Code-Blöcke

```java
public class Test {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
```

## Horizontale Linien

---

***

___

## Kombinierte Formatierung

**Fetter Text mit [Link](https://example.com)** und *kursiver Text* in einem Absatz.

> **Blockquote mit fettem Text** und *kursivem Text*.
> 
> Auch mit [Links](https://example.com) funktioniert es!
