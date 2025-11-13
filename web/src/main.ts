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

        frameElement.onload = () => {
            const width = frameElement.naturalWidth;
            const height = frameElement.naturalHeight;
            statsElement.textContent = `Frame stats → Resolution: ${width}×${height} | FPS: N/A (static sample) | Loaded at ${new Date().toLocaleString()}`;
        };

        frameElement.src = `data:image/png;base64,${base64}`;
    } catch (error) {
        console.error("Failed to load sample image", error);
        statsElement.textContent = "Failed to load sample image.";
    }
}

void loadSample();
