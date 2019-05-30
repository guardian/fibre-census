import React from 'react';
import PropTypes from 'prop-types';

class DisplayBox extends React.Component {
    static propTypes = {
        title: PropTypes.string,
        entry: PropTypes.object.isRequired,
        extraClasses: PropTypes.string,
        listData: PropTypes.array
    };

    /**
     * override this in a subclass to put actual body content into the box
     * @returns {*}
     */
    renderBodyContent(){
        return <p/>
    }

    render(){
        const classNameList = this.props.extraClasses ? "displaybox  " + this.props.extraClasses  : "displaybox";
        return <div className={classNameList}>
            <span className="displaybox-title">{this.props.title}</span>
            <div className="displaybox-content">
                {
                    this.renderBodyContent()
                }
            </div>
        </div>
    }
}

export default DisplayBox;