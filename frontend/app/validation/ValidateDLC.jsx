import ValidationComponent from "./ValidationComponent.jsx";

const mustHaveVolumes = [
    "false"
];


class ValidateDLC extends ValidationComponent {
    performValidation() {
        if(!this.props.listData){
            this.setState({tooltip: "No data provided"});
            return "problem";
        }
        const matches = mustHaveVolumes.map(volName=>this.props.listData.includes(volName)).filter(hasEntry=>hasEntry);

        if(matches.length!==mustHaveVolumes.length){
            this.setState({tooltip: "Expecting this value to be false"});
            return "problem";
        }
        return "normal";
    }
}

export default ValidateDLC;