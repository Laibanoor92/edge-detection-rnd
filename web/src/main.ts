async function loadSample(): Promise<void> {
    const frameElement = document.getElementById("frame") as HTMLImageElement | null;
    const statsElement = document.getElementById("stats");
    if (!frameElement || !statsElement) {
        console.error("Required DOM elements not found.");
        return;
    }

    try {
        const response = await fetch("../static/sample_processed.txt");
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const base64 = (await response.text()).trim();
        frameElement.src = `data:image/png;base64,${base64}`;
        statsElement.textContent = `Loaded at ${new Date().toLocaleString()}`;
    } catch (error) {
        console.error("Failed to load sample image", error);
        statsElement.textContent = "Failed to load sample image.";
    }
}

void loadSample();
