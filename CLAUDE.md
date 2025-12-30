# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Maven project containing ImageJ2/Fiji plugin demos showcasing ImgLib2 image processing and BigDataViewer (BDV) visualization capabilities. The project is developed by BIOP at EPFL.

## Build Commands

```bash
# Build the project
mvn clean install

# Package without tests
mvn clean package -DskipTests

# Run tests
mvn test
```

## Project Structure

- `src/main/java/ch/epfl/biop/demos/` - Demo command classes (ImageJ2 plugins)
- `src/main/java/ch/epfl/biop/demos/utils/` - Utility classes for demos
- `src/test/java/ch/epfl/biop/` - Test classes and launchers

## Key Frameworks & Dependencies

- **ImageJ2/SciJava**: Plugin framework using `@Plugin` annotations and `@Parameter` for UI
- **BigDataViewer (BDV)**: Visualization library for large multi-dimensional datasets
- **ImgLib2**: Core image processing library
- **N5/N5-Universe**: Data format for large datasets
- **MOBiE IO**: Image loading utilities

## Code Conventions

- Demo classes should follow the naming pattern `Demo*Command.java`
- All demo commands implement `Command` interface and use `@Plugin(type = Command.class)`
- Use `@Parameter` annotations for user inputs with appropriate `label` and `style` attributes
- BDV sources should be created using `BdvFunctions` or helper utilities in `utils/` package

## Testing

Run `SimpleIJLaunch` to start a local ImageJ instance with all plugins loaded for manual testing.
