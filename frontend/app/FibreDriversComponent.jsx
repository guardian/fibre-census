import React from 'react';
import PropTypes from 'prop-types';

class FibreDriversComponent extends React.Component {
    static propTypes = {
        entryList: PropTypes.object.isRequired,
        showDetails: PropTypes.bool
    };

    renderSingleEntry(entry){
        //{entry.driverName}@{entry.version}; dependencies {entry.dependencies.toLowerCase()}; loaded: {entry.loaded ? "yes" : "no"}
        return <li key={entry.driverName} className={entry.loaded ? "" : "driver-not-loaded"}>
            <span style={{display: "block"}}>{entry.driverName}</span>
            <ul className="addressList sub-list-deep" style={{display: this.props.showDetails ? "inherit" : "none" }}>
                <li className="small-info">{entry.getInfoString}</li>
                <li className="small-info">version: {entry.version}</li>
                <li className="small-info">dependencies {entry.dependencies.toLowerCase()}</li>
                <li className="small-info">loaded: {entry.loaded ? "yes" : "no"}</li>
            </ul>
        </li>
    }

    render(){
        if(!this.props.entryList) return <span className="small-info">not present</span>;

        return <ul className="addressList">{
            this.props.entryList.map(entry=>this.renderSingleEntry(entry))
        }</ul>
    }
}

export default FibreDriversComponent;