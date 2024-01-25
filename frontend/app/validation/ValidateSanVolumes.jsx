import ValidationComponent from "./ValidationComponent.jsx";

const mustHaveVolumes = [
    "false"
];


class ValidateSanVolumes extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({tooltip: "No data provided"});
            return "warning";
        }
        const matches = mustHaveVolumes.map(volName=>this.props.listData.includes(volName)).filter(hasEntry=>hasEntry);

        if(matches.length!==mustHaveVolumes.length){
            this.setState({tooltip: "Expecting this value to be false"});
            return "warning";
        }
        return "normal";
    }
}

export default ValidateSanVolumes;