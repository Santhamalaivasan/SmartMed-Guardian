# SmartMed Guardian Phase 1 Implementation Plan

This plan covers the foundation of the SmartMed Guardian application, focusing on the professional architecture (MVVM + Hilt), premium Neumorphic UI, and basic navigation.

## User Review Required

> [!IMPORTANT]
> The UI will strictly follow Neumorphism design principles, which differs significantly from standard Material Design.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///D:/SmartMedGuarian/gradle/libs.versions.toml)
Add Hilt, Navigation, and Lifecycle dependencies.

#### [MODIFY] [build.gradle.kts](file:///D:/SmartMedGuarian/build.gradle.kts)
Apply Hilt plugin at the project level.

#### [MODIFY] [build.gradle.kts](file:///D:/SmartMedGuarian/app/build.gradle.kts)
Apply Hilt and Kapt/KSP plugins. Add dependencies for Hilt, Navigation, and Compose.

### Core Architecture

#### [NEW] [SmartMedGuardianApp](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/SmartMedGuardianApp.kt)
Hilt Application class.

#### [MODIFY] [MainActivity](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/MainActivity.kt)
Entry point, entry to Navigation Graph.

### UI Components (Neumorphism)

#### [NEW] [NeumorphicTheme](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/theme/Theme.kt)
Custom colors and theme for Neumorphism.

#### [NEW] [NeumorphicComponents](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/components/NeumorphicComponents.kt)
Reusable Neumorphic components:
- NeumorphicButton
- NeumorphicCard
- NeumorphicTextField
- NeumorphicTopBar
- NeumorphicFAB

### Navigation

#### [NEW] [NavGraph](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/navigation/NavGraph.kt)
Define routes and navigation logic.

### Screens & ViewModels

#### [NEW] [Splash Screen & ViewModel](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/screens/splash/)
#### [NEW] [Home Screen & ViewModel](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/screens/home/)
#### [NEW] [Camera Screen (Placeholder) & ViewModel](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/screens/camera/)
#### [NEW] [Medicine Details Screen (Placeholder) & ViewModel](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/screens/details/)
#### [NEW] [Settings Screen & ViewModel](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/ui/screens/settings/)

### Data Layer

#### [NEW] [MedicineRepository](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/repository/MedicineRepository.kt)
Repository interface and implementation.

#### [NEW] [DI Modules](file:///D:/SmartMedGuarian/app/src/main/java/com/example/smartmedguarian/di/AppModule.kt)
Hilt modules for dependency injection.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds.
- (Optional) Basic unit tests for ViewModels.

### Manual Verification
- Deploy to device/emulator.
- Verify Splash screen timer (2s).
- Navigate through all screens (Home -> Camera, Home -> Settings, etc.).
- Verify Neumorphic UI appearance.
