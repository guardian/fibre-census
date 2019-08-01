import ValidationComponent from "./ValidationComponent.jsx";

const mustHaveVolumes = [
    "Multimedia2",
    "Proxies2",
    "StudioPipe2"
];


class ValidateSanVolumes extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({tooltip: "No data provided"});
            return "warning";
        }
        const matches = mustHaveVolumes.map(volName=>this.props.listData.includes(volName)).filter(hasEntry=>hasEntry);

        if(matches.length!==mustHaveVolumes.length){
            this.setState({tooltip: "Expecting volumes " + mustHaveVolumes});
            return "warning";
        }
        return "normal";
    }
}

export default ValidateSanVolumes;